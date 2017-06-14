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
package org.wso2.siddhi.extension.store.mongodb.util;

import com.mongodb.DBObject;
import com.mongodb.MongoClientOptions;
import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.extension.store.mongodb.MongoCompiledCondition;
import org.wso2.siddhi.extension.store.mongodb.exception.MongoTableException;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.APPLICATION_NAME;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.CONNECTIONS_PER_HOST;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.CONNECT_TIMEOUT;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.CURSOR_FINALIZER_ENABLED;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.HEARTBEAT_CONNECT_TIMEOUT;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.HEARTBEAT_FREQUENCY;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.HEARTBEAT_SOCKET_TIMEOUT;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.LOCAL_THRESHOLD;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.MAX_CONNECTION_IDLE_TIME;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.MAX_CONNECTION_LIFE_TIME;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.MAX_WAIT_TIME;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.MIN_CONNECTIONS_PER_HOST;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.MIN_HEARTBEAT_FREQUENCY;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.READ_CONCERN;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.READ_PREFERENCE;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.REG_INDEX_BY;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.REQUIRED_REPLICA_SET_NAME;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.SERVER_SELECTION_TIMEOUT;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.SOCKET_KEEP_ALIVE;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.SOCKET_TIMEOUT;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.SSL_ENABLED;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.THREADS_ALLOWED_TO_BLOCK;
import static org.wso2.siddhi.extension.store.mongodb.util.MongoTableConstants.WRITE_CONCERN;


/**
 * Class which holds the utility methods which are used by various units in the MongoDB Event Table implementation.
 */
public class MongoTableUtils {
    private static final Log log = LogFactory.getLog(MongoTableUtils.class);

    private MongoTableUtils() {
        //Prevent Initialization.
    }

    /**
     * Utility method which can be used to check if the given primary key is valid i.e. non empty
     * and is made up of attributes and return an index model when PrimaryKey is valid.
     *
     * @param primaryKey     the PrimaryKey annotation which contains the primary key attributes.
     * @param attributeNames List containing names of the attributes.
     * @return List of String with primary key attributes.
     */
    public static IndexModel extractPrimaryKey(Annotation primaryKey, List<String> attributeNames) {
        if (primaryKey == null) {
            return null;
        }
        Document primaryKeyIndex = new Document();
        primaryKey.getElements().forEach(
                element -> {
                    if (!isEmpty(element.getValue()) && attributeNames.contains(element.getValue())) {
                        primaryKeyIndex.append(element.getValue(), 1);
                    } else {
                        throw new MongoTableException("Annotation '" + primaryKey.getName() + "' contains value '" +
                                element.getValue() + "' which is not present in the attributes of the Event Table.");
                    }
                }
        );
        return new IndexModel(primaryKeyIndex, new IndexOptions().unique(true));
    }

    /**
     * Utility method which can be used to check if the given Indices are valid  and return List of
     * MongoDB Index Models when valid.
     *
     * @param indices        the IndexBy annotation which contains the indices definitions.
     * @param attributeNames List containing names of the attributes.
     * @return List of IndexModel.
     */
    public static List<IndexModel> extractIndexModels(Annotation indices, List<String> attributeNames) {
        if (indices == null) {
            return new ArrayList<>();
        }
        Pattern pattern = Pattern.compile(REG_INDEX_BY);
        return indices.getElements().stream().map(index -> {
            Matcher matcher = pattern.matcher(index.getValue());
            if (matcher.matches()) {
                if (attributeNames.contains(matcher.group(1))) {
                    return createIndexModel(matcher.group(1), Integer.parseInt(matcher.group(2)), matcher.group(3));
                } else {
                    throw new MongoTableException("Annotation '" + indices.getName() + "' contains illegal " +
                            "value(s). Please check your query and try again.");
                }
            } else {
                if (attributeNames.contains(index.getValue())) {
                    return createIndexModel(index.getValue(), 1, null);
                }
                throw new MongoTableException("Annotation '" + indices.getName() + "' contains illegal value(s). " +
                        "Please check your query and try again.");
            }
        }).collect(Collectors.toList());
    }

    /**
     * Utility method which can be used to create an IndexModel.
     *
     * @param fieldName   the attribute on which the index is to be created.
     * @param sortOrder   the sort order of the index to be created.
     * @param indexOption json string of the options of the index to be created.
     * @return IndexModel.
     */
    private static IndexModel createIndexModel(String fieldName, Integer sortOrder, String indexOption) {
        Document indexDocument = new Document(fieldName, sortOrder);
        if (indexOption == null) {
            return new IndexModel(indexDocument);
        } else {
            IndexOptions indexOptions = new IndexOptions();
            Document indexOptionDocument;
            try {
                indexOptionDocument = Document.parse(indexOption);
                for (Map.Entry<String, Object> indexEntry : indexOptionDocument.entrySet()) {
                    Object value = indexEntry.getValue();
                    switch (indexEntry.getKey()) {
                        case "unique":
                            indexOptions.unique(Boolean.parseBoolean(value.toString()));
                            break;
                        case "background":
                            indexOptions.background(Boolean.parseBoolean(value.toString()));
                            break;
                        case "name":
                            indexOptions.name(value.toString());
                            break;
                        case "sparse":
                            indexOptions.sparse(Boolean.parseBoolean(value.toString()));
                            break;
                        case "expireAfterSeconds":
                            indexOptions.expireAfter(Long.parseLong(value.toString()), TimeUnit.SECONDS);
                            break;
                        case "version":
                            indexOptions.version(Integer.parseInt(value.toString()));
                            break;
                        case "weights":
                            indexOptions.weights((Bson) value);
                            break;
                        case "languageOverride":
                            indexOptions.languageOverride(value.toString());
                            break;
                        case "defaultLanguage":
                            indexOptions.defaultLanguage(value.toString());
                            break;
                        case "textVersion":
                            indexOptions.textVersion(Integer.parseInt(value.toString()));
                            break;
                        case "sphereVersion":
                            indexOptions.sphereVersion(Integer.parseInt(value.toString()));
                            break;
                        case "bits":
                            indexOptions.bits(Integer.parseInt(value.toString()));
                            break;
                        case "min":
                            indexOptions.min(Double.parseDouble(value.toString()));
                            break;
                        case "max":
                            indexOptions.max(Double.parseDouble(value.toString()));
                            break;
                        case "bucketSize":
                            indexOptions.bucketSize(Double.parseDouble(value.toString()));
                            break;
                        case "partialFilterExpression":
                            indexOptions.partialFilterExpression((Bson) value);
                            break;
                        case "collation":
                            DBObject collationOptions = (DBObject) value;
                            Collation.Builder builder = Collation.builder();
                            for (String collationKey : collationOptions.keySet()) {
                                String collationObj = value.toString();
                                switch (collationKey) {
                                    case "locale":
                                        builder.locale(collationObj);
                                        break;
                                    case "caseLevel":
                                        builder.caseLevel(Boolean.parseBoolean(collationObj));
                                        break;
                                    case "caseFirst":
                                        builder.collationCaseFirst(CollationCaseFirst.fromString(collationObj));
                                        break;
                                    case "strength":
                                        builder.collationStrength(CollationStrength.valueOf(collationObj));
                                        break;
                                    case "numericOrdering":
                                        builder.numericOrdering(Boolean.parseBoolean(collationObj));
                                        break;
                                    case "normalization":
                                        builder.normalization(Boolean.parseBoolean(collationObj));
                                        break;
                                    case "backwards":
                                        builder.backwards(Boolean.parseBoolean(collationObj));
                                        break;
                                    case "alternate":
                                        builder.collationAlternate(CollationAlternate.fromString(collationObj));
                                        break;
                                    case "maxVariable":
                                        builder.collationMaxVariable(CollationMaxVariable.fromString(collationObj));
                                        break;
                                    default:
                                        log.warn("Annotation 'IndexBy' for the field '" + fieldName + "' contains " +
                                                "unknown 'Collation' Option key : '" + collationKey + "'. Please " +
                                                "check your query and try again.");
                                        break;
                                }
                            }
                            if (builder.build().getLocale() != null) {
                                indexOptions.collation(builder.build());
                            } else {
                                throw new MongoTableException("Annotation 'IndexBy' for the field '" + fieldName + "'" +
                                        " do not contain option for locale. Please check your query and try again.");
                            }
                            break;
                        case "storageEngine":
                            indexOptions.storageEngine((Bson) value);
                            break;
                        default:
                            log.warn("Annotation 'IndexBy' for the field '" + fieldName + "' contains unknown option " +
                                    "key : '" + indexEntry.getKey() + "'. Please check your query and try again.");
                            break;
                    }
                }
            } catch (JsonParseException | NumberFormatException e) {
                throw new MongoTableException("Annotation 'IndexBy' for the field '" + fieldName + "' contains " +
                        "illegal value(s) for index option. Please check your query and try again.", e);
            }
            return new IndexModel(indexDocument, indexOptions);
        }
    }

    /**
     * Utility method which can be used to resolve the condition with the runtime values and return a Document
     * describing the filter.
     *
     * @param compiledCondition     the compiled condition which was built during compile time and now is being provided
     *                              by the Siddhi runtime.
     * @param conditionParameterMap the map which contains the runtime value(s) for the condition.
     * @return Document.
     */
    public static Document resolveCondition(MongoCompiledCondition compiledCondition,
                                            Map<String, Object> conditionParameterMap) {
        Map<String, Object> parameters = compiledCondition.getPlaceholders();
        String compiledQuery = compiledCondition.getCompiledQuery();
        if (compiledQuery.equalsIgnoreCase("true")) {
            return new Document();
        }
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object parameter = entry.getValue();
            Attribute variable = (Attribute) parameter;
            if (variable.getType().equals(Attribute.Type.STRING)) {
                compiledQuery = compiledQuery.replaceAll(entry.getKey(), "\"" +
                        conditionParameterMap.get(variable.getName()).toString() + "\"");
            } else {
                compiledQuery = compiledQuery.replaceAll(entry.getKey(),
                        conditionParameterMap.get(variable.getName()).toString());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("The final compiled query : '" + compiledQuery + "'");
        }
        return Document.parse(compiledQuery);
    }

    /**
     * Utility method which can be used to check if a given string instance is null or empty.
     *
     * @param field the string instance to be checked.
     * @return true if the field is null or empty.
     */
    public static boolean isEmpty(String field) {
        return (field == null || field.trim().length() == 0);
    }


    /**
     * Utility method tp map the values to the respective attributes before database writes.
     *
     * @param record              Object array of the runtime values.
     * @param attributesPositions Map containing the attribute position and name.
     * @return Document
     */
    public static Map<String, Object> mapValuesToAttributes(Object[] record, Map<Integer, String> attributesPositions) {
        Map<String, Object> attributesValuesMap = new HashMap<>();
        for (int i = 0; i < record.length; i++) {
            attributesValuesMap.put(attributesPositions.get(i), record[i]);
        }
        return attributesValuesMap;
    }

    /**
     * Utility method which can be used to check if the existing indices contain the expected indices
     * defined by the annotation 'PrimaryKey' and 'IndexBy' and log a warning when indices differs.
     *
     * @param existingIndices List of indices that the collection contains.
     * @param expectedIndices List of indices that are defined by the annotations.
     */
    public static void checkExistingIndices(List<IndexModel> expectedIndices, MongoCursor<Document> existingIndices) {
        Map<String, Object> indexOptionsMap = new HashMap<>();
        List<Document> expectedIndexDocuments = expectedIndices.stream().map(expectedIndex -> {
            IndexOptions expectedIndexOptions = expectedIndex.getOptions();
            indexOptionsMap.put("key", expectedIndex.getKeys());
            // Default value for name of the index
            if (expectedIndexOptions.getName() == null) {
                StringBuilder indexName = new StringBuilder();
                ((Document) expectedIndex.getKeys()).forEach((key, value) ->
                        indexName.append("_").append(key).append("_").append(value));
                indexName.deleteCharAt(0);
                indexOptionsMap.put("name", indexName.toString());
            } else {
                indexOptionsMap.put("name", expectedIndexOptions.getName());
            }
            // Default value for the version
            if (expectedIndexOptions.getVersion() == null) {
                indexOptionsMap.put("v", 2);
            } else {
                indexOptionsMap.put("v", expectedIndexOptions.getVersion());
            }
            indexOptionsMap.put("unique", expectedIndexOptions.isUnique());
            indexOptionsMap.put("background", expectedIndexOptions.isBackground());
            indexOptionsMap.put("sparse", expectedIndexOptions.isSparse());
            indexOptionsMap.put("expireAfterSeconds", expectedIndexOptions.getExpireAfter(TimeUnit.SECONDS));
            indexOptionsMap.put("weights", expectedIndexOptions.getWeights());
            indexOptionsMap.put("languageOverride", expectedIndexOptions.getLanguageOverride());
            indexOptionsMap.put("defaultLanguage", expectedIndexOptions.getDefaultLanguage());
            indexOptionsMap.put("textVersion", expectedIndexOptions.getTextVersion());
            indexOptionsMap.put("sphereVersion", expectedIndexOptions.getSphereVersion());
            indexOptionsMap.put("textVersion", expectedIndexOptions.getTextVersion());
            indexOptionsMap.put("bits", expectedIndexOptions.getBits());
            indexOptionsMap.put("min", expectedIndexOptions.getMin());
            indexOptionsMap.put("max", expectedIndexOptions.getMax());
            indexOptionsMap.put("bucketSize", expectedIndexOptions.getBucketSize());
            indexOptionsMap.put("partialFilterExpression", expectedIndexOptions.getPartialFilterExpression());
            indexOptionsMap.put("collation", expectedIndexOptions.getCollation());
            indexOptionsMap.put("storageEngine", expectedIndexOptions.getStorageEngine());

            //Remove if Default Values - these would not be in the existingIndexDocument.
            indexOptionsMap.values().removeIf(Objects::isNull);
            indexOptionsMap.remove("unique", false);
            indexOptionsMap.remove("background", false);
            indexOptionsMap.remove("sparse", false);

            return new Document(indexOptionsMap);
        }).collect(Collectors.toList());

        List<Document> existingIndexDocuments = new ArrayList<>();
        existingIndices.forEachRemaining(existingIndex -> {
            existingIndex.remove("ns");
            existingIndexDocuments.add(existingIndex);
        });

        if (!existingIndexDocuments.containsAll(expectedIndexDocuments)) {
            log.warn("Existing indices differs from the expected indices defined by the Annotations 'PrimaryKey' " +
                    "and 'IndexBy'.\nExisting Indices '" + existingIndexDocuments.toString() + "'.\n" +
                    "Expected Indices '" + expectedIndexDocuments.toString() + "'");
        }
    }

    /**
     * Utility method which can be used to create MongoClientOptionsBuilder from values defined in the
     * deployment yaml file.
     *
     * @param configReader {@link ConfigReader} Configuration Reader
     */
    public static MongoClientOptions.Builder extractMongoClientOptionsBuilder(ConfigReader configReader) {
        MongoClientOptions.Builder mongoClientOptionsBuilder = MongoClientOptions.builder();
        try {
            mongoClientOptionsBuilder.connectionsPerHost(
                    Integer.parseInt(configReader.readConfig(CONNECTIONS_PER_HOST, "100")));
            mongoClientOptionsBuilder.connectTimeout(
                    Integer.parseInt(configReader.readConfig(CONNECT_TIMEOUT, "10000")));
            mongoClientOptionsBuilder.heartbeatConnectTimeout(
                    Integer.parseInt(configReader.readConfig(HEARTBEAT_CONNECT_TIMEOUT, "20000")));
            mongoClientOptionsBuilder.heartbeatSocketTimeout(
                    Integer.parseInt(configReader.readConfig(HEARTBEAT_SOCKET_TIMEOUT, "20000")));
            mongoClientOptionsBuilder.heartbeatFrequency(
                    Integer.parseInt(configReader.readConfig(HEARTBEAT_FREQUENCY, "10000")));
            mongoClientOptionsBuilder.localThreshold(
                    Integer.parseInt(configReader.readConfig(LOCAL_THRESHOLD, "15")));
            mongoClientOptionsBuilder.maxWaitTime(
                    Integer.parseInt(configReader.readConfig(MAX_WAIT_TIME, "120000")));
            mongoClientOptionsBuilder.minConnectionsPerHost(
                    Integer.parseInt(configReader.readConfig(MIN_CONNECTIONS_PER_HOST, "0")));
            mongoClientOptionsBuilder.minHeartbeatFrequency(
                    Integer.parseInt(configReader.readConfig(MIN_HEARTBEAT_FREQUENCY, "500")));
            mongoClientOptionsBuilder.serverSelectionTimeout(
                    Integer.parseInt(configReader.readConfig(SERVER_SELECTION_TIMEOUT, "30000")));
            mongoClientOptionsBuilder.socketTimeout(
                    Integer.parseInt(configReader.readConfig(SOCKET_TIMEOUT, "0")));
            mongoClientOptionsBuilder.threadsAllowedToBlockForConnectionMultiplier(Integer.parseInt(
                    configReader.readConfig(THREADS_ALLOWED_TO_BLOCK, "5")));
            mongoClientOptionsBuilder.socketKeepAlive(
                    Boolean.parseBoolean(configReader.readConfig(SOCKET_KEEP_ALIVE, "false")));
            mongoClientOptionsBuilder.sslEnabled(
                    Boolean.parseBoolean(configReader.readConfig(SSL_ENABLED, "false")));
            mongoClientOptionsBuilder.cursorFinalizerEnabled(
                    Boolean.parseBoolean(configReader.readConfig(CURSOR_FINALIZER_ENABLED, "true")));
            mongoClientOptionsBuilder.readPreference(
                    ReadPreference.valueOf(configReader.readConfig(READ_PREFERENCE, "primary")));
            mongoClientOptionsBuilder.writeConcern(
                    WriteConcern.valueOf(configReader.readConfig(WRITE_CONCERN, "acknowledged")));

            String readConcern = configReader.readConfig(READ_CONCERN, "DEFAULT");
            if (!readConcern.matches("DEFAULT")) {
                mongoClientOptionsBuilder.readConcern(new ReadConcern(
                        ReadConcernLevel.fromString(readConcern)));
            }

            int maxConnectionIdleTime = Integer.parseInt(configReader.readConfig(MAX_CONNECTION_IDLE_TIME, "0"));
            if (maxConnectionIdleTime != 0) {
                mongoClientOptionsBuilder.maxConnectionIdleTime(maxConnectionIdleTime);
            }

            int maxConnectionLifeTime = Integer.parseInt(configReader.readConfig(MAX_CONNECTION_LIFE_TIME, "0"));
            if (maxConnectionIdleTime != 0) {
                mongoClientOptionsBuilder.maxConnectionLifeTime(maxConnectionLifeTime);
            }

            String requiredReplicaSetName = configReader.readConfig(REQUIRED_REPLICA_SET_NAME, "");
            if (requiredReplicaSetName.equals("")) {
                mongoClientOptionsBuilder.requiredReplicaSetName(requiredReplicaSetName);
            }

            String applicationName = configReader.readConfig(APPLICATION_NAME, "");
            if (applicationName.equals("")) {
                mongoClientOptionsBuilder.applicationName(applicationName);
            }

            return mongoClientOptionsBuilder;
        } catch (IllegalArgumentException e) {
            throw new MongoTableException("Values Read from config readers have illegal values : ", e);
        }
    }
}

