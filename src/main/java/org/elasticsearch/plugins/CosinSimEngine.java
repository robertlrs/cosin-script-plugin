package org.elasticsearch.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author luorenshu(626115221 @ qq.com)
 * @date 2018/11/2 下午4:28
 **/

public class CosinSimEngine implements ScriptEngine {
    private final static Logger logger = LogManager.getLogger(CosinSimEngine.class);
    private static final String FIELD = "field";
    private static String VECTOR = "vector";
    private static String NEGATIVE_TO_ZERO = "negative_to_zero";

    @Override
    public String getType() {
        // script name
        return "cosin-sim-plugin";
    }

    @Override
    @SuppressWarnings (value="unchecked")
    public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
        if (!context.equals(SearchScript.CONTEXT)) {
            throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
        }

        // we use the script "source" as the script identifier
        if (VECTOR.equals(scriptSource)) {
            SearchScript.Factory factory = CosinLeafFactory::new;
            return context.factoryClazz.cast(factory);
        }

        throw new IllegalArgumentException("Unknown script name " + scriptSource);
    }


    private static class CosinLeafFactory implements SearchScript.LeafFactory{
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final String field;
        private List<Double> vector;

        private CosinLeafFactory(Map<String, Object> params, SearchLookup lookup){
            this.params = params;
            this.lookup = lookup;

            if (!params.containsKey(FIELD)) {
                throw new IllegalArgumentException("Missing parameter [field]");
            }

            if (!params.containsKey(VECTOR)) {
                throw new IllegalArgumentException("Missing parameter [vector]");
            }

            field = params.get(FIELD).toString();
            this.genVector(params.get(VECTOR));
        }

        @Override
        public SearchScript newInstance(LeafReaderContext context) throws IOException {
            return new CosinScript(params, lookup, context, field, vector);
        }

        @Override
        public boolean needs_score() {
            return false;
        }

        private void genVector(Object object){
            if (object == null){
                throw new IllegalArgumentException("parameter object is null in method genVector!");
            }

            if (object instanceof List){
                vector = (List<Double>) object;
            }else{
                throw new IllegalArgumentException("parameter [vector] type error, it must be List<Double> or String!");
            }
        }
    }

    private static class CosinScript extends SearchScript {
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final LeafReaderContext leafContext;
        private final String field;
        private final List<Double> queryVector;


        public CosinScript(Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext, String field, List<Double> vector) {
            super(params, lookup, leafContext);

            this.params = params;
            this.lookup = lookup;
            this.leafContext = leafContext;
            this.field = field;
            this.queryVector = vector;
        }

        @Override
        public double runAsDouble() {
            try {

                List<Double> vector;

                /*
                 *  this field value doesn't read from DocValues, which is slow than read value from doc vlaues,
                 *  in order to read value from doc values, we must store the vector as a str splited by ",", cause double array
                 *  can't not store in doc values
                 *  TODO: use binary coding instead of vectorStr
                 */
//                Object object = lookup.source().get(field);
                SortedSetDocValues docValues = DocValues.getSortedSet(leafContext.reader(), field);
                if (docValues == null){
                    return 0.0;
                }

                StringBuilder  vectorBuilder = new StringBuilder();
                long ord;
                while ((ord = docValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    BytesRef bytesRef = docValues.lookupOrd(ord);
                    vectorBuilder.append(bytesRef.utf8ToString());
                }

                String vectorStr =  vectorBuilder.toString();
                if (null == vectorStr){
                    return 0.0;
                }

                vectorStr = vectorStr.trim();
                if ( vectorStr.length() == 0){

                    return 0.0;
                } else {
                    String[] values = vectorStr.split(",");
                    vector = new ArrayList<>();

                    for (int i = 0; i < values.length; i++) {
                        vector.add(Double.valueOf(values[i]).doubleValue());
                    }
                }

                int size = Math.min(this.queryVector.size(), vector.size());
//                if (this.queryVector.size() != vector.size()) {
//                    //TODO: throw Exception ?
//                    logger.warn("vector size is not equal " + field + "vector size:" + vector.size());
//                }

                double sum1 = 0, sum2 = 0, queryValue=0, fieldValue=0, dot=0, score=0;
                for (int i = 0; i < size; i++) {
                    queryValue = this.queryVector.get(i);
                    fieldValue = vector.get(i);
                    dot += queryValue * fieldValue;
                    sum1 += queryValue * queryValue;
                    sum2 += fieldValue * fieldValue;
                }

                double denominator = (Math.sqrt(sum1) * Math.sqrt(sum2));
                if (denominator == 0){
                    score = 0;
                }else{
                    score = dot / denominator;
                }

                if (score < 0 && null != params && params.containsKey(NEGATIVE_TO_ZERO)){
                    boolean is_negative_to_zero = (boolean) params.get(NEGATIVE_TO_ZERO);
                    if (is_negative_to_zero){
                        score = 0;
                    }
                }
                
                return score;
            } catch (Exception e) {
                logger.error(e);
                throw new ElasticsearchGenerationException("Dot product calculation of field : " + field + " error, " + e.getMessage(), e);
            }
        }
    }


    @Override
    public void close() {
        // optionally close resources
    }
}
