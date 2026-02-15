import java.io.*;

public class LatLonToXYConverter {

    public static void main(String[] args) {
        String inputDirectoryPath = "C:\\Users\\86151\\Desktop\\identifiable"; // 输入文件夹路径
        String outputDirectoryPath = "F:\\论文数据\\XYConverter identifiable 0-1000"; // 输出文件夹路径
        
        File inputDirectory = new File(inputDirectoryPath);
        File[] files = inputDirectory.listFiles((dir, name) -> name.endsWith(".csv"));

        if (files != null) {
            for (File file : files) {
                try {
                    convertFile(file, outputDirectoryPath);
                } catch (IOException e) {
                    System.err.println("Error processing file: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    // 转换单个文件
    private static void convertFile(File inputFile, String outputDirectoryPath) throws IOException {
        String outputFileName = outputDirectoryPath + "/" + inputFile.getName().replace(".csv", "_converted.csv");

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(","); // 假设每行数据用逗号分隔
                if (parts.length < 2) continue;

                double latitude = Double.parseDouble(parts[0].trim());
                double longitude = Double.parseDouble(parts[1].trim());

                // 墨卡托投影转换
                double[] xy = mercatorProjection(latitude, longitude);

                // 写入转换后的平面坐标
                writer.write(xy[0] + "," + xy[1]);
                writer.newLine();
            }
        }
    }

    // 墨卡托投影转换方法
    private static double[] mercatorProjection(double latitude, double longitude) {
        final double RADIUS = 6378137.0; // WGS84地球半径（长半轴）
        double x = longitude * RADIUS * Math.PI / 180;
        double y = Math.log(Math.tan((90 + latitude) * Math.PI / 360)) * RADIUS;
        return new double[]{x, y};
    }
}
