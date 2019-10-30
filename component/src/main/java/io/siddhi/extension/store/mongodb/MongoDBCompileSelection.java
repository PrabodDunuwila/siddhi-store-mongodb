
package io.siddhi.extension.store.mongodb;

import io.siddhi.core.util.collection.operator.CompiledSelection;

public class MongoDBCompileSelection implements CompiledSelection {

    private String compileSelectQuery;
    private String having;
    private Long limit;
    private Long offset;

    public MongoDBCompileSelection(String project, String having, Long limit, Long offset){
        this.compileSelectQuery = project;
        this.having = having;
        this.limit = limit;
        this.offset = offset;
    }

    public String getCompileSelectQuery() {
        return compileSelectQuery;
    }

    public String getHaving(){
        return this.having;
    }

    public Long getLimit(){
        return this.limit;
    }

    public Long getOffset(){
        return this.offset;
    }

}
