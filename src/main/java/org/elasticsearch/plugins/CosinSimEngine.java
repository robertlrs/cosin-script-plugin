package org.elasticsearch.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
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

    @Override
    public String getType() {
        // script name
        return "cosinSimPlugin";
    }

    @Override
    @SuppressWarnings (value="unchecked")
    public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
        if (!context.equals(SearchScript.CONTEXT)) {
            throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
        }

        // we use the script "source" as the script identifier
        if ("vector".equals(scriptSource)) {
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

    private static class CosinScript extends SearchScript{
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final LeafReaderContext leafContext;
        private final String field;
        private final List<Double> vector;


        public CosinScript(Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext, String field, List<Double> vector) {
            super(params, lookup, leafContext);

            this.params = params;
            this.lookup = lookup;
            this.leafContext = leafContext;
            this.field = field;
            this.vector = vector;
        }

        @Override
        public double runAsDouble() {
            try {
                if (!lookup.source().containsKey(field)) {
                    return 0;
                }

                List<Double> titleVec;

                //TODO: is this field value read from DocValues ?
                Object object = lookup.source().get(field);
                if (null == object){
                    //TODO: better process ?
                    return 0.0;
                }else if (object instanceof List){
                   titleVec = (List<Double>) object;
                }else  if (object instanceof String){
                    String vectorStr = (String) object;
                    String[] values = vectorStr.split(",");
                    titleVec = new ArrayList<>();

                    for (int i = 0; i < values.length; i++) {
                        titleVec.add(Double.valueOf(values[i]).doubleValue());
                    }
                }else {
                    return 0.0;
                }

                int size = 0;
                if (vector.size() != titleVec.size()) {
                    //TODO: throw Exception ?
                    logger.warn("vector size is not equal " + field + "vector size:" + vector.size());
                    size = Math.max(vector.size(), titleVec.size());
                }

                double sum1 = 0, sum2 = 0, queryValue=0, fieldValue=0, dot=0;
                for (int i = 0; i < size; i++) {
                    queryValue = vector.get(i);
                    fieldValue = titleVec.get(i);
                    dot += queryValue * fieldValue;
                    sum1 += queryValue * queryValue;
                    sum2 += fieldValue * fieldValue;
                }

                return dot / (Math.sqrt(sum1) * Math.sqrt(sum2));
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
