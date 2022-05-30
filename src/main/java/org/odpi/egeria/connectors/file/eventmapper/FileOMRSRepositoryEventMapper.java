/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.eventmapper;


import org.odpi.egeria.connectors.file.auditlog.FileOMRSAuditCode;
import org.odpi.egeria.connectors.file.auditlog.FileOMRSErrorCode;
import org.odpi.egeria.connectors.file.repositoryconnector.FileOMRSMetadataCollection;
import org.odpi.egeria.connectors.file.repositoryconnector.FileOMRSRepositoryConnector;

import org.odpi.openmetadata.repositoryservices.events.OMRSInstanceEvent;

import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.repositoryservices.connectors.openmetadatatopic.OpenMetadataTopicListener;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryeventmapper.OMRSRepositoryEventMapperBase;

import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.ClassificationErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityConflictException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotDeletedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.EntityProxyOnlyException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.FunctionNotSupportedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.HomeEntityException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.HomeRelationshipException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidEntityException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidParameterException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidRelationshipException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.InvalidTypeDefException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.OMRSLogicErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.PagingErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.PatchErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.PropertyErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipConflictException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipNotDeletedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RelationshipNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.StatusNotSupportedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefConflictException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefInUseException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefNotKnownException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefNotSupportedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.UserNotAuthorizedException;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefPatch;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryValidator;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;


import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FileOMRSRepositoryEventMapper supports the event mapper function for Apache File
 * when used as an open metadata repository.
 */
public class FileOMRSRepositoryEventMapper extends OMRSRepositoryEventMapperBase
//        implements OpenMetadataTopicListener
{

    private static final String DATA_FILE = "DataFile";
    private static final String CONNECTION = "Connection";
    private static final String CONNECTOR_TYPE = "ConnectorType";
    private static final String ENDPOINT = "Endpoint";
    private static final String CONNECTION_ENDPOINT = "ConnectionEndpoint";
    private static final String CONNECTION_CONNECTOR_TYPE = "ConnectionConnectorType";
    private static final String CONNECTION_TO_ASSET = "ConnectionToAsset";
    //    private static final Logger log = LoggerFactory.getLogger(FileOMRSRepositoryEventMapper.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private FileOMRSRepositoryConnector fileRepositoryConnector;
    private FileOMRSMetadataCollection fileMetadataCollection;

    //    private String metadataCollectionId;
//    private String originatorServerName;
//    private String originatorServerType;
    Map<String, String> typeNameToGuidMap =null;



    private PollingThread pollingThread;
//    private EntityMessageDeserializer deserializer;


    /**
     * Default constructor
     */
    public FileOMRSRepositoryEventMapper() {
        super();
//        this.sourceName = "FileOMRSRepositoryEventMapper";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized public void start() throws ConnectorCheckedException {

        super.start();

        final String methodName = "start";

        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_STARTING.getMessageDefinition());

        if (!(repositoryConnector instanceof FileOMRSRepositoryConnector)) {
            raiseConnectorCheckedException(FileOMRSErrorCode.EVENT_MAPPER_IMPROPERLY_INITIALIZED, methodName, null, repositoryConnector.getServerName());
        }
        this.fileRepositoryConnector = (FileOMRSRepositoryConnector) this.repositoryConnector;
        this.repositoryHelper = this.fileRepositoryConnector.getRepositoryHelper();



        // this.deserializer = new EntityMessageDeserializer();

        this.pollingThread = new PollingThread();
        try {
            this.fileMetadataCollection = (FileOMRSMetadataCollection) fileRepositoryConnector.getMetadataCollection();
        } catch (RepositoryErrorException e) {
            raiseConnectorCheckedException(FileOMRSErrorCode.COLLECTION_FAILED_INITIALISE, methodName, e, fileRepositoryConnector.getServerName());
        }
//        this.fileMetadataCollection.setEventMapper(this);

//        this.originatorServerName = fileRepositoryConnector.getServerName();
//        this.originatorServerType = fileRepositoryConnector.getServerType();
        pollingThread.start();

    }


    /**
     * Class to poll for file content
     */
    private class PollingThread implements Runnable {

        void start() {
            Thread worker = new Thread(this);
            worker.start();
        }

        void stop() {
            if (!running.compareAndSet(true, false)) {
                auditLog.logMessage("stop", FileOMRSAuditCode.POLLING_THREAD_INFO_ALREADY_STOPPED.getMessageDefinition());
            }
        }

        //        void processEvent(String event) {
//            //no op
//        }
        private List<EntityDetail> getEntitiesByType(String typeName) throws ConnectorCheckedException {
            String methodName = "getEntitiesByType(String typeName)";
            List<EntityDetail> entityDetails = null;
            try {
                entityDetails = getEntitiesByTypeGuid(typeName);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (PropertyErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (PagingErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PAGING_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (FunctionNotSupportedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName());
            }
            return entityDetails;
        }

        private List<Relationship> getRelationshipsForEntityHelper(
                String entityGUID,
                String relationshipTypeGUID) throws ConnectorCheckedException {
            String methodName = "getRelationshipsForEntityHelper";
            List<Relationship> relationships = null;
            try {
                relationships = fileMetadataCollection.getRelationshipsForEntity("userId", entityGUID, relationshipTypeGUID, 0, null, null, null, null, 0);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (PropertyErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (PagingErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PAGING_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (FunctionNotSupportedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (EntityNotKnownException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, fileRepositoryConnector.getServerName(), methodName, entityGUID);
            }
            return relationships;
        }
        private EntityDetail getEntityDetail(String userId, String guid) throws ConnectorCheckedException {
            String methodName = "getEntityDetail";
            EntityDetail entityDetail = null;
            try {
                entityDetail = fileMetadataCollection.getEntityDetail(userId, guid);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, fileRepositoryConnector.getServerName(), methodName);
            } catch (EntityNotKnownException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, fileRepositoryConnector.getServerName(), methodName, guid);
            } catch (EntityProxyOnlyException e){
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_PROXY_ONLY, methodName, e, fileRepositoryConnector.getServerName(), methodName, guid);
            }
            return entityDetail;


        }
        void sendBatchEvent() throws ConnectorCheckedException {
            String methodName = "sendBatchEvent";
            List<EntityDetail> dataFiles = getEntitiesByType(DATA_FILE);

            for (EntityDetail dataFile : dataFiles) {

                List<Relationship> relationshipList = new ArrayList<>();
                List<EntityDetail> entityList = new ArrayList<>();
                entityList.add(dataFile);
                String assetGUID = dataFile.getGUID();
                List<String> connectionGuids = populateEvent(CONNECTION_TO_ASSET, assetGUID, entityList, relationshipList);
                if (connectionGuids != null && connectionGuids.size() >0) {
                    for (String connectionGUID : connectionGuids) {
                        populateEvent(CONNECTION_CONNECTOR_TYPE, connectionGUID, entityList, relationshipList);
                        populateEvent(CONNECTION_ENDPOINT, connectionGUID, entityList, relationshipList);
                    }
                }

                InstanceGraph instances = new InstanceGraph(entityList, relationshipList);

                // send the event
                repositoryEventProcessor.processInstanceBatchEvent("FileOMRSRepositoryEventMapper",
                                                                   fileRepositoryConnector.getMetadataCollectionId(),
                                                                   fileRepositoryConnector.getServerName(),
                                                                   fileRepositoryConnector.getServerType(),
                                                                   fileRepositoryConnector.getOrganizationName(),
                                                                   instances);
            }

        }
        private List<String> populateEvent(String relationshipTypeName, String startEntityGUID, List<EntityDetail> entityList, List<Relationship> relationshipList) throws ConnectorCheckedException {
            String methodName = "populateEvent";

            List<String> otherEndGuids = new ArrayList<>();
            TypeDefSummary typeDefSummary = repositoryHelper.getTypeDefByName(methodName, relationshipTypeName);
            String relationshipTypeGUID = typeDefSummary.getGUID();
            List<Relationship> connectorConnectorTypeRelationships = getRelationshipsForEntityHelper(startEntityGUID, relationshipTypeGUID);
            for (Relationship relationship: connectorConnectorTypeRelationships) {
                EntityProxy proxy = repositoryHelper.getOtherEnd(methodName,
                                                                 startEntityGUID,
                                                                 relationship);
                String guid = proxy.getGUID();
                EntityDetail otherEndEntity = getEntityDetail("Userid", guid);
                entityList.add(otherEndEntity);
                relationshipList.add(relationship);
                otherEndGuids.add(otherEndEntity.getGUID());
            }
            return otherEndGuids;

        }
        /**
         * Read File.
         */
        @Override
        public void run() {

            final String methodName = "run";
            if (running.compareAndSet(false, true)) {

                final List<String> supportedTypeNames = Arrays.asList(new String[]{
                        // entity types
                        "DataStore", // super type of Datafile
                        "Asset", // super type of Datastore
                        "Referenceable", // super type of the others
                        "OpenMetadataRoot", // super type of referenceable
                        DATA_FILE,
                        CONNECTION,
                        CONNECTOR_TYPE,
                        ENDPOINT,
                        // relationship types
                        CONNECTION_ENDPOINT,
                        CONNECTION_CONNECTOR_TYPE,
                        CONNECTION_TO_ASSET
                        // classification types
                        // none at this time
                });
                final int supportedCount = supportedTypeNames.size();

                int typesAvailableCount = 0;
                int retryCount = 0;
                while (running.get()) {
                    try {
                        while ((typesAvailableCount != supportedCount) && retryCount < 10) {
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP.getMessageDefinition(typesAvailableCount + "", supportedCount + "", retryCount + ""));
                            // only come out the while loop when we can get all of the supported types in one iteration.
                            typesAvailableCount = 0;
                            if (typeNameToGuidMap == null) {
                                typeNameToGuidMap = new HashMap<>();
                            }
                            // populate the type name to guid map
                            for (String typeName : supportedTypeNames) {

                                TypeDef typeDef = repositoryHelper.getTypeDefByName("FileOMRSRepositoryEventMapper",
                                                                                    typeName);
                                if (typeDef != null) {
                                    auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_FOUND_TYPE.getMessageDefinition(typeName));
                                    typeNameToGuidMap.put(typeName, typeDef.getGUID());
                                    typesAvailableCount++;
                                }
                            }
                            if (typesAvailableCount < supportedCount) {
                                //delay for 1 second and then retry
                                auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_PRE_WAIT.getMessageDefinition());
                                try {
                                    Thread.sleep(1000);
                                    retryCount++;
                                    auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_POST_WAIT.getMessageDefinition(retryCount + ""));
                                } catch (InterruptedException e) {
                                    // should not happen as there is only one thread
                                    // if it happens then continue in the while
                                    auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_INTERRUPTED_EXCEPTION.getMessageDefinition());
                                }
                            } else if (typesAvailableCount == supportedCount) {
                                // log to say we have all the types we need
                                auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRED_ALL_TYPES.getMessageDefinition());

                            }

                        }
                        if (retryCount == 10) {

                            //TODO error
                        } else {
//
                            // call the repository connector to refresh its contents.
                            fileRepositoryConnector.refreshRepository();
                            sendBatchEvent();

                        }
                        //  scope a call /calls to the repository connector for example for a file
                        //  wait the polling interval.
                        //delay for 5 second and then retry
                        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_PRE_WAIT.getMessageDefinition());
                        try {
                            Thread.sleep(5000);
                            retryCount++;
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_POST_WAIT.getMessageDefinition(retryCount + ""));
                        } catch (InterruptedException e) {
                            // should not happen as there is only one thread
                            // if it happens then continue in the while
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_INTERRUPTED_EXCEPTION.getMessageDefinition());
                        }
                    } catch (ConnectorCheckedException e) {
                        if (e.getCause() == null) {
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_GOT_AN_EXCEPTION.getMessageDefinition(e.getMessage()));
                        } else {
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_GOT_AN_EXCEPTION_WITH_CAUSE.getMessageDefinition(e.getMessage(), e.getCause().getMessage()));
                        }
                    } finally {
                        // stop the thead if we came out of the loop.
                        this.stop();
                    }
                }
            }
        }

        private List<EntityDetail> getEntitiesByTypeGuid(String typeName) throws
                                                                          InvalidParameterException,
                                                                          RepositoryErrorException,
                                                                          TypeErrorException,
                                                                          PropertyErrorException,
                                                                          PagingErrorException,
                                                                          FunctionNotSupportedException,
                                                                          UserNotAuthorizedException {
            String typeGUID = typeNameToGuidMap.get(typeName);
            if (typeGUID == null) {
//                throw new RepositoryErrorException()
                // TODO
                return null;
            } else {
                return fileMetadataCollection.findEntities(
                        "userId",    //TODO get from config
                        typeGUID,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0);
            }
        }


        // Uncomment when we start to use this method
//        private List<Relationship> getRelationshipsByTypeGuid(String typeName) throws
//                                                                          InvalidParameterException,
//                                                                          RepositoryErrorException,
//                                                                          TypeErrorException,
//                                                                          PropertyErrorException,
//                                                                          PagingErrorException,
//                                                                          FunctionNotSupportedException,
//                                                                          UserNotAuthorizedException {
//            String typeGUID = typeNameToGuidMap.get(typeName);
//            if (typeGUID == null) {
//                // TODO throw Exception
//                return null;
//            } else {
//                return fileMetadataCollection.findRelationships(
//                        "userId",    //TODO get from config
//                       typeGUID,
//                        null,
//                        null,
//                        0,
//                        null,
//                        null,
//                        null,
//                        null,
//                        0);
//            }
//        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    synchronized public void disconnect() throws ConnectorCheckedException {
        super.disconnect();
        final String methodName = "disconnect";
        pollingThread.stop();
        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_SHUTDOWN.getMessageDefinition(fileRepositoryConnector.getServerName()));
    }


//    /**
//     * Sends a refresh entity request event.
//     *
//     * @param typeDefGUID unique identifier of requested entity's TypeDef
//     * @param typeDefName unique name of requested entity's TypeDef
//     * @param entityGUID unique identifier of requested entity
//     * @param homeMetadataCollectionId identifier of the metadata collection that is the home to this entity
//     */
//    public void sendRefreshEntityRequest(String typeDefGUID,
//                                         String typeDefName,
//                                         String entityGUID,
//                                         String homeMetadataCollectionId) {
//        repositoryEventProcessor.processRefreshEntityRequested(
//                sourceName,
//                metadataCollectionId,
//                localServerName,
//                localServerType,
//                localOrganizationName,
//                typeDefGUID,
//                typeDefName,
//                entityGUID,
//                homeMetadataCollectionId);
//    }
//
//    /**
//     * Sends a refresh relationship request event.
//     *
//     * @param typeDefGUID the guid of the TypeDef for the relationship used to verify the relationship identity
//     * @param typeDefName the name of the TypeDef for the relationship used to verify the relationship identity
//     * @param relationshipGUID unique identifier of the relationship
//     * @param homeMetadataCollectionId unique identifier for the home repository for this relationship
//     */
//    public void sendRefreshRelationshipRequest(String typeDefGUID,
//                                               String typeDefName,
//                                               String relationshipGUID,
//                                               String homeMetadataCollectionId) {
//        repositoryEventProcessor.processRefreshRelationshipRequest(
//                sourceName,
//                metadataCollectionId,
//                localServerName,
//                localServerType,
//                localOrganizationName,
//                typeDefGUID,
//                typeDefName,
//                relationshipGUID,
//                homeMetadataCollectionId);
//    }

    /**
     * Throws a ConnectorCheckedException based on the provided parameters.
     *
     * @param errorCode the error code for the exception
     * @param methodName the method name throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any additional parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(FileOMRSErrorCode errorCode, String methodName, Exception cause, String ...params) throws ConnectorCheckedException {
        if (cause == null) {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                                                this.getClass().getName(),
                                                methodName);
        } else {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                                                this.getClass().getName(),
                                                methodName,
                                                cause);
        }
    }


}
