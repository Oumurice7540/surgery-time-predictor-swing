package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.*;
import java.io.Serializable;
import java.util.*;

/** A fitted, serializable schema. Fit only on the training partition. */
public final class FittedTransforms implements Serializable {
    private static final long serialVersionUID=1L;
    public record Field(String key,String name,boolean numeric,List<String> categories,double impute,
                        List<Scale> scales,int offset,int width) implements Serializable { }
    public record Scale(String operation,double origin,double divisor) implements Serializable { }
    private final List<Field> fields;
    private final List<String> names;
    private FittedTransforms(List<Field> fields,List<String> names){this.fields=List.copyOf(fields);this.names=List.copyOf(names);}
    public List<Field> fields(){return fields;}
    public List<String> names(){return names;}
    public static boolean transformation(ProcessingStep s){return switch(s.operation()){case CATEGORY_CODES,MIN_MAX,Z_SCORE->true;default->false;};}
    public static FittedTransforms fit(ProcessingTable table,List<ProcessingTable.Row> training,List<String> selected,List<ProcessingStep> steps){
        if(training.isEmpty())throw new IllegalArgumentException("沒有訓練資料可估計轉換");
        var fields=new ArrayList<Field>();var names=new ArrayList<String>();
        for(String group:selected){
            var col=table.columns().stream().filter(c->group.equals(c.feature()) || (c.key().startsWith("extra:")&&group.equals(c.key()))).findFirst().orElseThrow(()->new IllegalArgumentException("找不到特徵："+group));
            if(col.key().equals("case_id")||col.key().equals("actual_minutes"))throw new IllegalArgumentException("識別碼與目標不可當特徵");
            var values=training.stream().map(row->row.values().getOrDefault(col.key(),"").strip()).toList();
            var present=values.stream().filter(v->!v.isEmpty()).toList();
            if(present.isEmpty())throw new IllegalArgumentException("訓練集欄位全部缺值："+col.label());
            var operations=steps.stream().filter(FittedTransforms::transformation).filter(s->s.column().equals(col.key())).toList();
            int codes=-1;
            for(int i=0;i<operations.size();i++)if(operations.get(i).operation()==ProcessingStep.Operation.CATEGORY_CODES){if(codes>=0||i>0)throw new IllegalArgumentException("類別數值化須為該欄第一個轉換且只能一次："+col.label());codes=i;}
            boolean categorical=operations.stream().anyMatch(s->s.operation()==ProcessingStep.Operation.CATEGORY_CODES);
            boolean numeric=!categorical&&present.stream().allMatch(FittedTransforms::isNumber);
            var categories=numeric?List.<String>of():present.stream().distinct().sorted().toList();
            double impute=numeric?present.stream().mapToDouble(Double::parseDouble).average().orElseThrow():-1;
            int width=numeric||categorical?1:categories.size()+2;
            if(width>5000 || names.size()+width>20000)throw new IllegalArgumentException("類別數過多，疑似識別碼，請取消選取："+col.label());
            var numbers=new ArrayList<Double>();
            for(String v:values)numbers.add(numeric?(v.isEmpty()?impute:Double.parseDouble(v)):(double)(v.isEmpty()?-1:categories.indexOf(v)));
            var scales=new ArrayList<Scale>();
            for(var op:operations){
                if(op.operation()==ProcessingStep.Operation.CATEGORY_CODES)continue;
                if(!numeric&&!categorical)throw new IllegalArgumentException("文字欄位需先加入類別數值化："+col.label());
                double origin,divisor;
                if(op.operation()==ProcessingStep.Operation.MIN_MAX){origin=numbers.stream().mapToDouble(x->x).min().orElseThrow();divisor=numbers.stream().mapToDouble(x->x).max().orElseThrow()-origin;}
                else {origin=numbers.stream().mapToDouble(x->x).average().orElseThrow();final double mean=origin;divisor=Math.sqrt(numbers.stream().mapToDouble(x->(x-mean)*(x-mean)).average().orElseThrow());}
                if(divisor==0)divisor=1;
                final double a=origin,b=divisor;scales.add(new Scale(op.operation().name(),a,b));numbers.replaceAll(x->(x-a)/b);
            }
            int offset=names.size();
            if(width==1)names.add(group);else {for(String c:categories)names.add(group+"="+c);names.add(group+"=<missing>");names.add(group+"=<unknown>");}
            fields.add(new Field(col.key(),group,numeric,categories,impute,List.copyOf(scales),offset,width));
        }
        if((long)training.size()*names.size()>10_000_000L)throw new IllegalArgumentException("編碼後矩陣過大，請減少高基數類別欄位或先使用類別數值化");
        return new FittedTransforms(fields,names);
    }
    public double[] encode(Map<String,String> row){
        var out=new double[names.size()];
        for(var f:fields){String v=row.getOrDefault(f.key(),"").strip();
            if(f.width()==1){double n;
                if(f.numeric()){if(!v.isEmpty()&&!isNumber(v))throw new IllegalArgumentException("欄位須為有限數值："+f.name());n=v.isEmpty()?f.impute():Double.parseDouble(v);}
                else n=v.isEmpty()?-1:f.categories().indexOf(v); // Unknown and missing category code -1; dictionary never expands.
                for(var scale:f.scales())n=(n-scale.origin())/scale.divisor();
                if(!Double.isFinite(n))throw new IllegalArgumentException("轉換結果超出數值範圍："+f.name());out[f.offset()]=n;
            }else{int i=v.isEmpty()?f.categories().size():f.categories().indexOf(v);if(i<0)i=f.categories().size()+1;out[f.offset()+i]=1;}
        }return out;
    }
    public Map<String,String> byFeature(Map<String,String> values){var out=new LinkedHashMap<String,String>();for(var f:fields)out.put(f.key(),values.getOrDefault(f.name(),""));return out;}
    public String description(){var out=new StringBuilder("僅使用訓練集估計；數值缺值以訓練平均補值；未見類別使用 unknown／-1。\n");for(var f:fields)out.append(f.name()).append(": ").append(f.numeric()?"數值，缺值="+f.impute():"字典="+f.categories()).append(" ").append(f.scales()).append('\n');return out.toString();}
    private static boolean isNumber(String v){try{return Double.isFinite(Double.parseDouble(v));}catch(Exception e){return false;}}
}
