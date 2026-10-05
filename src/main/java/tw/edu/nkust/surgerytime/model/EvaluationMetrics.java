package tw.edu.nkust.surgerytime.model;
import java.io.Serializable;
public record EvaluationMetrics(int count,double mae,double rmse,double rSquared,long evaluationMillis) implements Serializable {
    public EvaluationMetrics {
        if(count<1||!Double.isFinite(mae)||mae<0||!Double.isFinite(rmse)||rmse<0||!Double.isFinite(rSquared)||rSquared>1||evaluationMillis<0)
            throw new IllegalArgumentException("評估指標無效");
    }
}
