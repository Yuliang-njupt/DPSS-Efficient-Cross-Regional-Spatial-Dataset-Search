# DPSS-Efficient-Cross-Regional-Spatial-Dataset-Search

This repository contains the code for the paper **"Efficient Cross-Regional Spatial Dataset Search with Kernel Density Estimation"**. [cite_start]This research proposes a dataset retrieval scheme aimed at addressing the challenge of discovering datasets with similar distribution patterns in cross-regional scenarios[cite: 8].

## 🖼️ System Overview

[cite_start]The following figure illustrates the DPSS+ framework, which is bifurcated into an offline processing stage for repository indexing and an online query stage for rapid similarity retrieval[cite: 53].

![System Architecture](figure2.png) 

* [cite_start]**(a) Data Modeling**: Raw spatial points are normalized to mitigate geographic offsets and transformed into continuous probability fields using Kernel Density Estimation (KDE)[cite: 49, 183].
* [cite_start]**(b) Dimension Reduction**: Grid cells are mapped to a one-dimensional sequence using Hilbert curves to preserve spatial locality[cite: 154, 321].
* [cite_start]**(c) Index Construction**: A balanced binary search tree (HBT-index) is built based on Hilbert-compressed representations[cite: 328, 329].
* [cite_start]**(d) Coarse-grained Filtering**: Chebyshev’s inequality is used to prune dissimilar candidates efficiently by calculating potential upper and lower bounds[cite: 320, 330].
* [cite_start]**(e) Fine-grained Matching**: The Jensen-Shannon Divergence (JSD) is calculated for the remaining candidates to find the Top-k results[cite: 165, 250].

---

## 🛠️ Experiment Settings

### 1. Default Parameters
The system's performance is governed by several key parameters defined in the `Test_Optimized.java` file:

| Parameter | Meaning | Default Value |
| :--- | :--- | :--- |
| `θ` (theta) | Grid resolution parameter ($2^\theta \times 2^\theta$) | 7 |
| `α` (alpha) | Bandwidth-density sensitivity parameter | 0.2 |
| `λ` (lamda) | Filtering threshold parameter for HBT-index | 0.6 |
| `k` | Number of top similar datasets to return | 5 |

### 2. Dataset Repositories
The experiments are conducted on three real-world repositories:
* [cite_start]**Identifiable**: Precise addresses of buildings and POIs[cite: 393].
* [cite_start]**Public**: Large-scale public geographic databases[cite: 393].
* [cite_start]**Trackable**: GPS trajectories of mobile targets[cite: 393].

---

## 🚀 How to Run

### 1. Prerequisites
* Java Development Kit (JDK) 1.8 or higher.
* At least 16GB of RAM (128GB recommended for full-scale 100k dataset tests).

### 2. Configuration
Before running the program, you must update the local data path in `src/Similarity_Model/Test_Optimized.java`:

```java
// Modify this line to point to your local dataset directory
String directoryPath = "C:\\Your\\Path\\To\\Datasets";
