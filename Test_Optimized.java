package Similarity_Model;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static Similarity_Model.Probability_density_distribution.*;
import static Similarity_Model.Read_Files.readCsvFilesFromDirectory;
import static Similarity_Model.Index.*;

public class Test_Optimized {
    private static final int [] resolutions = {8, 16, 32, 64, 128};//网格划分参数，2的整次幂;256/512/1024/2048
    private static final int [] Top_Ks = {3, 5, 10, 15, 20};
    private static final int [] Sizes = {1000};
    private static final double [] alphas = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6};//带宽控制参数
    private static final double [] lamdas = {0.2, 0.4, 0.6, 0.8, 1.0, 1.2};//过滤参数
    private static final int iterations = 100;

    public static void main(String[] args) {
        int Top_K = 5;
        double lamda = 0.6;
        int resolution = 32;
        double alpha = 0.2;
        int Size = 1000;

        for (int a = 0; a < Top_Ks.length; a++) {
            Top_K = Top_Ks[a];
            String directoryPath = "F:\\论文数据\\XYConverter identifiable 0-" + Size;
            //String pdfFilePath = directoryPath + "_noKDE_" + resolution + "_"  + ".ser"; // 定义保存路径
            String pdfFilePath = directoryPath + "_PDFs_3h_" + resolution + "_" + alpha + ".ser";

            List<Map<GridIndex, Double>> PDFs;
            List<List<Double[]>> dataWarehouse = readCsvFilesFromDirectory(directoryPath);//所有文件都要读进来，后续要从中选取query

            File pdfFile = new File(pdfFilePath);
            if (pdfFile.exists()) {
                // 如果文件存在，加载 PDFs
                PDFs = DataPersistence.loadPDFsFromFile(pdfFilePath);
            } else {
                // 否则执行计算并保存 PDFs
                List<Map<GridIndex, Integer>> gridCounts = gridCounts(dataWarehouse, resolution);
                PDFs = calculateKDE(gridCounts, resolution, alpha, new GaussianKernel());// 计算所有数据集的kde很耗时间
                //if (alpha == 0.2) {
                    DataPersistence.savePDFsToFile(PDFs, pdfFilePath);  // 保存 PDFs
                //}
            }

            System.out.println("resolution: " + resolution);
            System.out.println("alpha: " + alpha);
            System.out.println("lamda: " + lamda);
            System.out.println("Top_K: " + Top_K);
            System.out.println("Sizes: " + Size);

            Random random = new Random(42); // 随机数生成器

            //构建索引树
            Map<Integer, double[]> stats = calculateHilbertStats(PDFs, resolution);
            buildIndexTree(stats);

            // 初始化用于累加时间的变量
            long totalBaselineSearchTime = 0;
            long totalOptimizedLocalKDESearchTime = 0;
            long totalOptimizedPruningGlobalKDESearchTime = 0;
            long totalOptimizedLocalKDEPruningSearchTime = 0;
            long totalGridCountsTime = 0;
            long totalKDE3hTime = 0;
            long totalKDEGlobalTime = 0;
            long totalStats3hTime = 0;
            long totalStatsGlobalTime = 0;
            double totalSimilarity = 0;
            //long totalSimCount = 0;
            double totalSC = 0;
            double totalMSE = 0; // 替换 totalSC

            for (int iter = 0; iter < iterations; iter++) {
                // 处理query
                int sampleIdx = random.nextInt(dataWarehouse.size());
                List<Double[]> sampleDataset = dataWarehouse.get(sampleIdx);
                List<List<Double[]>> singleSampleList = new ArrayList<>();
                singleSampleList.add(sampleDataset);

                // 记录 gridCounts 时间
                long gridCountsStartTime = System.currentTimeMillis();
                List<Map<GridIndex, Integer>> singleSampleGridCounts = gridCounts(singleSampleList, resolution);
                long gridCountsEndTime = System.currentTimeMillis();
                long gridCountsTime = gridCountsEndTime - gridCountsStartTime;
                totalGridCountsTime += gridCountsTime;

                // 记录 3h 范围限制的 KDE 计算时间
                long kde3hStartTime = System.currentTimeMillis();
                List<Map<GridIndex, Double>> singleSamplePDFs3h = calculateKDE(singleSampleGridCounts, resolution, alpha, new GaussianKernel());
                long kde3hEndTime = System.currentTimeMillis();
                long kde3hTime = kde3hEndTime - kde3hStartTime;
                totalKDE3hTime += kde3hTime;

                Map<GridIndex, Double> sample3h = singleSamplePDFs3h.get(0);

                // 记录计算 sample3h 的 Hilbert 编码均值和方差的时间
                long stats3hStartTime = System.currentTimeMillis();
                List<Map<GridIndex, Double>> singleSamplePDFList3h = new ArrayList<>();
                singleSamplePDFList3h.add(sample3h);
                Map<Integer, double[]> singleSampleStats3h = calculateHilbertStats(singleSamplePDFList3h, resolution);
                double[] sampleStats3h = singleSampleStats3h.get(0);
                double targetMean3h = sampleStats3h[0];
                double targetVariance3h = sampleStats3h[1];
                long stats3hEndTime = System.currentTimeMillis();
                long stats3hTime = stats3hEndTime - stats3hStartTime;
                totalStats3hTime += stats3hTime;

                long searchStartTime;
                long searchEndTime;
                long searchTime;

                // optimizied(局部KDE+剪枝)
                searchStartTime = System.currentTimeMillis();
                List<Integer> candidates2 = filterCandidates(targetMean3h, targetVariance3h, lamda);
                //JSD
                List<DatasetSimilarity> optimizedResults3 = findTopKWithCandidates(sample3h, PDFs, candidates2, Top_K);
                //overlapDensity
                //List<DatasetSimilarity> optimizedResults3 = findTopKWithCandidates_OverlapDensity(sample3h, PDFs, candidates2, Top_K);
                searchEndTime = System.currentTimeMillis();
                searchTime = searchEndTime - searchStartTime;
                totalOptimizedLocalKDEPruningSearchTime += searchTime;
                //System.out.println(sampleIdx + " " + optimizedResults3.get(0).index);

                for (int i = 0; i < optimizedResults3.size(); i++) {
                    //System.out.println(optimizedResults3.get(i).jsd);
                    totalSimilarity += optimizedResults3.get(i).jsd;
                }

//                // 创建一个新的列表来存储索引
//                List<Integer> topKIndices = new ArrayList<>();
//                if (optimizedResults3 != null) {
//                    for (DatasetSimilarity result : optimizedResults3) {
//                        topKIndices.add(result.index);
//                    }
//                }
//                //Top-k数据集作为结果簇，再选择10倍的数据集作为对比簇
//                int sampleSize = 10 * topKIndices.size();
//                List<Integer> contrastIndices = RandomSampling(PDFs, topKIndices, sampleSize);
//
//                // 步骤2：预计算JSD
//                Map<Pair<Integer>, Double> jsdCache = new HashMap<>();
//                // 预计算Top-K内部的JSD
//                for (int i : topKIndices) {
//                    for (int j : topKIndices) {
//                        if (i != j) {
//                            double jsd = Similarity_Calculator.calculateJSD(
//                                    PDFs.get(i),
//                                    PDFs.get(j)
//                            );
//                            jsdCache.put(new Pair(i, j), jsd);
//                        }
//                    }
//                }
//                // 预计算Top-K与对比簇的JSD
//                for (int i : topKIndices) {
//                    for (int j : contrastIndices) {
//                        double jsd = Similarity_Calculator.calculateJSD(
//                                PDFs.get(i),
//                                PDFs.get(j)
//                        );
//                        jsdCache.put(new Pair(i, j), jsd);
//                    }
//                }
//                double SC = Similarity_Calculator.calculateSilhouetteCoefficient(jsdCache, topKIndices, contrastIndices);
//                totalSC += SC;

                // +++ 新增的 MSE 评估逻辑开始 +++
                double iterationMSE = 0;
                if (optimizedResults3 != null && !optimizedResults3.isEmpty()) {
                    for (DatasetSimilarity result : optimizedResults3) {
                        // 计算 Query (sample3h) 与每一个检索结果 (PDFs.get(result.index)) 的 MSE
                        double mse = Similarity_Calculator.calculateMSE(sample3h, PDFs.get(result.index));
                        iterationMSE += mse;
                    }
                    // 取当前这组 Top-K 的平均 MSE
                    double averageIterationMSE = iterationMSE / optimizedResults3.size();
                    totalMSE += averageIterationMSE; // 需要在循环外定义 totalMSE 变量
                }
                // +++ 新增的 MSE 评估逻辑结束 +++
            }

            // 计算平均值
            long averageOptimizedLocalKDEPruningSearchTime = totalOptimizedLocalKDEPruningSearchTime / iterations;
            long averageGridCountsTime = totalGridCountsTime / iterations;
            long averageKDE3hTime = totalKDE3hTime / iterations;
            long averageStats3hTime = totalStats3hTime / iterations;
            double averageSimilarity = totalSimilarity / (iterations * Top_K);
            long totalTime = averageStats3hTime + averageKDE3hTime + averageGridCountsTime + averageOptimizedLocalKDEPruningSearchTime;
            double averageSC = totalSC / iterations;
            double averageMSE = totalMSE / iterations;

            // 输出平均值
            System.out.println("所有循环的平均值：");
            System.out.println("Optimized 搜索平均时间: " + averageOptimizedLocalKDEPruningSearchTime + " 毫秒");
            //System.out.println("gridCounts 平均时间: " + averageGridCountsTime + " 毫秒");
            //System.out.println("3h范围的 KDE 计算平均时间: " + averageKDE3hTime + " 毫秒");
            //System.out.println("3hKDE样本的希尔伯特编码平均时间： " + averageStats3hTime + " 毫秒" );
            System.out.println("总时间：" + totalTime + " 毫秒");
            System.out.println("平均JSD： " + averageSimilarity);
            // System.out.println("轮廓系数：" + averageSC);
            System.out.println("平均 MSE: " + averageMSE); // 替换轮廓系数的输出

            // 1. 计算baseline的PDF存储开销（与之前相同）
            int totalEntries = 0;
            for (Map<GridIndex, Double> pdf : PDFs) {
                totalEntries += pdf.size();
            }
            final long BASELINE_ENTRY_SIZE = 80; // HashMap.Entry+GridIndex+Double
            long baselineMemory = totalEntries * BASELINE_ENTRY_SIZE;

            // 2. 计算优化方案的附加开销
            int datasetCount = PDFs.size();

            // Hilbert统计Map的空间开销（Map<Integer, double[]>）
            final long STATS_ENTRY_SIZE = 80; // HashMap.Node(32) + Integer(16) + double[2](32)
            long statsMemory = datasetCount * STATS_ENTRY_SIZE;

            // 索引树节点的空间开销（每个TreeNode约32字节）
            final long TREE_NODE_SIZE = 32; // 对象头12B + double8B + int4B + 两个引用8B
            long treeMemory = datasetCount * TREE_NODE_SIZE;
            System.out.println("总索引空间成本: " + ((baselineMemory + statsMemory + treeMemory)/1024/1024) + " MB");
            System.out.println();
        }
    }

    public static List<Integer> RandomSampling(List<Map<GridIndex, Double>> allPDFs, List<Integer> exclude, int sampleSize) {
        // 生成所有索引的集合
        Set<Integer> allIndices = new HashSet<>();
        for (int i = 0; i < allPDFs.size(); i++) {
            allIndices.add(i);
        }

        // 排除指定的索引
        allIndices.removeAll(new HashSet<>(exclude));

        // 将剩余索引存储到列表中
        List<Integer> availableIndices = new ArrayList<>(allIndices);

        // 创建随机数生成器
        Random random = new Random(42);
        List<Integer> result = new ArrayList<>();

        // 随机采样
        int minSize = Math.min(sampleSize, availableIndices.size());
        while (result.size() < minSize) {
            int randomIndex = random.nextInt(availableIndices.size());
            int selectedIndex = availableIndices.get(randomIndex);
            result.add(selectedIndex);
            availableIndices.remove(randomIndex);
        }

        return result;
    }

    public static List<DatasetSimilarity> findTopKWithCandidates(
            Map<GridIndex, Double> sample,
            List<Map<GridIndex, Double>> PDFs,
            List<Integer> candidates,
            int k
    ) {
        // 使用优先队列维护 Top-K 结果
        PriorityQueue<DatasetSimilarity> topK = new PriorityQueue<>(
                Comparator.comparingDouble(ds -> -ds.jsd) // 按 JSD 从大到小排序,大根堆
        );
        for (int candidateIndex : candidates) {
            Map<GridIndex, Double> candidatePDF = PDFs.get(candidateIndex);

            // 计算 JSD
            double jsd = Similarity_Calculator.calculateJSD(sample, candidatePDF);

            // 构造 DatasetSimilarity 对象
            DatasetSimilarity similarity = new DatasetSimilarity(candidateIndex, candidatePDF, jsd);

            // 动态维护前 K 个最优解
            if (topK.size() < k) {
                topK.add(similarity); // 未达到 K 个时直接加入
            } else if (jsd < topK.peek().jsd) {
                // 如果当前 JSD 小于堆中最大 JSD，则替换最差解（最大值）
                topK.poll();
                topK.add(similarity);
            }
        }

        // 转换为列表并按 JSD 从小到大排序
        List<DatasetSimilarity> result = new ArrayList<>(topK);
        result.sort(Comparator.comparingDouble(ds -> ds.jsd));
        return result;
    }

    public static List<DatasetSimilarity> findTopKWithCandidates_OverlapDensity(
            Map<GridIndex, Double> sample,
            List<Map<GridIndex, Double>> PDFs,
            List<Integer> candidates,
            int k
    ) {
        // 使用优先队列维护 Top-K 结果
        PriorityQueue<DatasetSimilarity> topK = new PriorityQueue<>(
                Comparator.comparingDouble(ds -> -ds.jsd)
        );
        for (int candidateIndex : candidates) {
            Map<GridIndex, Double> candidatePDF = PDFs.get(candidateIndex);

            // 计算 Sim
            double jsd = Similarity_Calculator.calculateOverlapDensity(sample, candidatePDF);

            // 构造 DatasetSimilarity 对象
            DatasetSimilarity similarity = new DatasetSimilarity(candidateIndex, candidatePDF, jsd);

            // 动态维护前 K 个最优解
            if (topK.size() < k) {
                topK.add(similarity); // 未达到 K 个时直接加入
            } else if (jsd < topK.peek().jsd) {
                topK.poll();
                topK.add(similarity);
            }
        }

        // 转换为列表并按 JSD 从大到小排序
        List<DatasetSimilarity> result = new ArrayList<>(topK);
        result.sort(Comparator.comparingDouble(ds -> ds.jsd));
        return result;
    }
}

class Pair<Integer> {
    private Integer first;
    private Integer second;

    public Pair(Integer first, Integer second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass()!= o.getClass()) return false;
        Pair<?> pair = (Pair<?>) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}
