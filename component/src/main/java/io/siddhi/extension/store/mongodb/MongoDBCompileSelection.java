/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.siddhi.extension.store.mongodb;

import io.siddhi.core.util.collection.operator.CompiledCondition;
import io.siddhi.core.util.collection.operator.CompiledSelection;
import org.bson.Document;

import java.security.acl.LastOwnerException;
import java.util.Map;

/**
 * Implementation class of {@link CompiledCondition} corresponding to the MongoDB Event Table.
 * Maintains the condition string returned by the ConditionVisitor as well as a map of parameters to be used at runtime.
 */
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

    public void setCompileSelectQuery(Document compileSelectQuery) {
        this.compileSelectQuery = compileSelectQuery;
    }

}
