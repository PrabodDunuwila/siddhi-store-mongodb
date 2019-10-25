
package io.siddhi.extension.store.mongodb;

import io.siddhi.core.util.collection.operator.CompiledSelection;
import org.bson.Document;

public class MongoDBCompileSelection implements CompiledSelection {

    private Document compileSelectQuery;
    private Long limit;
    private Long offset;

    public MongoDBCompileSelection(Document project, Long limit, Long offset){
        this.compileSelectQuery = project;
        this.limit = limit;
        this.offset = offset;
    }

    public Document getCompileSelectQuery() {
        return compileSelectQuery;
    }

    public Long getLimitAggregation(){
        return this.limit;
    }

    public Long getOffsetAggregation(){
        return this.offset;
    }

}
