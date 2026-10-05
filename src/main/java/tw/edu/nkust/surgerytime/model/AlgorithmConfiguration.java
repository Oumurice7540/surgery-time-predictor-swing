package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;
import java.util.Objects;

/** 與 BPNN 設定分離，保留舊 .model 的 TrainingConfiguration 相容性。 */
public record AlgorithmConfiguration(Algorithm algorithm, int treeCount, int maxDepth,
                                     double ridge, double cost, double gamma) implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Algorithm {
        BPNN("BPNN 反向傳播神經網路"), RANDOM_FOREST("RandomForest 隨機森林"),
        LINEAR_REGRESSION("LinearRegression 線性迴歸"), SVR("SVR 支援向量迴歸（RBF）");
        private final String label;
        Algorithm(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static final AlgorithmConfiguration DEFAULT = defaults(Algorithm.BPNN);
    public static AlgorithmConfiguration defaults(Algorithm algorithm) {
        return new AlgorithmConfiguration(algorithm, 100, 0, 1e-8, 1.0, .01);
    }
    public AlgorithmConfiguration {
        Objects.requireNonNull(algorithm, "請選擇演算法");
        if (treeCount < 1 || treeCount > 1000) throw new IllegalArgumentException("樹數須介於 1–1000");
        if (maxDepth < 0 || maxDepth > 100) throw new IllegalArgumentException("最大深度須介於 0–100（0 不限制）");
        if (!Double.isFinite(ridge) || ridge < 0 || ridge > 100) throw new IllegalArgumentException("Ridge 須介於 0–100");
        if (!Double.isFinite(cost) || cost < .001 || cost > 10000) throw new IllegalArgumentException("C 須介於 0.001–10000");
        if (!Double.isFinite(gamma) || gamma < .000001 || gamma > 100) throw new IllegalArgumentException("Gamma 須介於 0.000001–100");
    }
    public String description(TrainingConfiguration training) {
        return switch(algorithm) {
            case BPNN -> "隱藏層 " + training.hiddenNeuronCount() + " · Epoch " + training.epochs()
                    + " · Alpha " + training.momentumAlpha() + " · Eta " + training.learningRate();
            case RANDOM_FOREST -> "樹數 " + treeCount + " · 最大深度 " + (maxDepth == 0 ? "不限制" : maxDepth);
            case LINEAR_REGRESSION -> "Ridge " + ridge + " · 無自動特徵選擇";
            case SVR -> "RBF · C " + cost + " · Gamma " + gamma + " · 訓練集正規化";
        };
    }
}
