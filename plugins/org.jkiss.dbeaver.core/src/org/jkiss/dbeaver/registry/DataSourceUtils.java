/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.registry;

import org.eclipse.core.resources.IProject;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceFolder;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.registry.driver.DriverDescriptor;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Connection spec utils.
 */
public class DataSourceUtils {

    public static final String PARAM_ID = "id";
    public static final String PARAM_DRIVER = "driver";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_URL = "url";
    public static final String PARAM_HOST = "host";
    public static final String PARAM_PORT = "port";
    public static final String PARAM_SERVER = "server";
    public static final String PARAM_DATABASE = "database";
    public static final String PARAM_USER = "user";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_SAVE_PASSWORD = "savePassword";
    public static final String PARAM_SHOW_SYSTEM_OBJECTS = "showSystemObjects";
    public static final String PARAM_SHOW_UTILITY_OBJECTS = "showUtilityObjects";
    public static final String PARAM_FOLDER = "folder";
    public static final String PARAM_AUTO_COMMIT = "autoCommit";

    private static final Log log = Log.getLog(DataSourceUtils.class);

    public static DBPDataSourceContainer getDataSourceBySpec(
        @NotNull IProject project,
        @NotNull String connectionSpec,
        @Nullable GeneralUtils.IParameterHandler parameterHandler,
        boolean searchByParameters,
        boolean createNewDataSource)
    {
        String driverName = null, url = null, host = null, port = null, server = null, database = null, user = null, password = null;
        boolean showSystemObjects = false, showUtilityObjects = false, savePassword = true;
        Boolean autoCommit = null;
        Map<String, String> conProperties = new HashMap<>();
        DBPDataSourceFolder folder = null;
        String dsId = null, dsName = null;

        DataSourceRegistry dsRegistry = DBeaverCore.getInstance().getProjectRegistry().getDataSourceRegistry(project);
        if (dsRegistry == null) {
            log.debug("No datasource registry for project '" + project.getName() + "'");
            return null;
        }

        String[] conParams = connectionSpec.split("\\|");
        for (String cp : conParams) {
            int divPos = cp.indexOf('=');
            if (divPos == -1) {
                continue;
            }
            String paramName = cp.substring(0, divPos);
            String paramValue = cp.substring(divPos + 1);
            switch (paramName) {
                case PARAM_ID:
                    dsId = paramValue;
                    break;
                case PARAM_DRIVER:
                    driverName = paramValue;
                    break;
                case PARAM_NAME:
                    dsName = paramValue;
                    break;
                case PARAM_URL:
                    url = paramValue;
                    break;
                case PARAM_HOST:
                    host = paramValue;
                    break;
                case PARAM_PORT:
                    port = paramValue;
                    break;
                case PARAM_SERVER:
                    server = paramValue;
                    break;
                case PARAM_DATABASE:
                    database = paramValue;
                    break;
                case PARAM_USER:
                    user = paramValue;
                    break;
                case PARAM_PASSWORD:
                    password = paramValue;
                    break;
                case PARAM_SAVE_PASSWORD:
                    savePassword = CommonUtils.toBoolean(paramValue);
                    break;
                case PARAM_SHOW_SYSTEM_OBJECTS:
                    showSystemObjects = CommonUtils.toBoolean(paramValue);
                    break;
                case PARAM_SHOW_UTILITY_OBJECTS:
                    showUtilityObjects = CommonUtils.toBoolean(paramValue);
                    break;
                case PARAM_FOLDER:
                    folder = dsRegistry.getFolder(paramValue);
                    break;
                case PARAM_AUTO_COMMIT:
                    autoCommit = CommonUtils.toBoolean(paramValue);
                    break;
                default:
                    boolean handled = false;
                    if (paramName.length() > 5 && paramName.startsWith("prop.")) {
                        paramName = paramName.substring(5);
                        conProperties.put(paramName, paramValue);
                        handled = true;
                    } else if (parameterHandler != null) {
                        handled = parameterHandler.setParameter(paramName, paramValue);
                    }
                    if (!handled) {
                        log.debug("Unknown connection parameter '" + paramName + "'");
                    }
            }
        }

        DBPDataSourceContainer dataSource = dsRegistry.getDataSource(dsId);
        if (dataSource != null) {
            return dataSource;
        }

        if (dsName != null) {
            dataSource = dsRegistry.findDataSourceByName(dsName);
            if (dataSource != null) {
                return dataSource;
            }
        }

        if (searchByParameters) {
            // Try to find by parameters
            if (url != null) {
                for (DBPDataSourceContainer ds : dsRegistry.getDataSources()) {
                    if (url.equals(ds.getConnectionConfiguration().getUrl())) {
                        if (user == null || user.equals(ds.getConnectionConfiguration().getUserName())) {
                            return ds;
                        }
                    }
                }
            }
        }

        if (!createNewDataSource) {
            return null;
        }

        if (driverName == null) {
            log.error("Driver name not specified - can't create new datasource");
            return null;
        }
        DriverDescriptor driver = DataSourceProviderRegistry.getInstance().findDriver(driverName);
        if (driver == null) {
            log.error("Driver '" + driverName + "' not found");
            return null;
        }

        // Create new datasource with specified parameters
        if (dsName == null) {
            dsName = "Ext: " + driver.getName();
            if (database != null) {
                dsName += " - " + database;
            } else if (server != null) {
                dsName += " - " + server;
            }
        }

        DBPConnectionConfiguration connConfig = new DBPConnectionConfiguration();
        connConfig.setUrl(url);
        connConfig.setHostName(host);
        connConfig.setHostPort(port);
        connConfig.setServerName(server);
        connConfig.setDatabaseName(database);
        connConfig.setUserName(user);
        connConfig.setUserPassword(password);
        connConfig.setProperties(conProperties);

        if (autoCommit != null) {
            connConfig.getBootstrap().setDefaultAutoCommit(autoCommit);
        }

        DataSourceDescriptor newDS = new DataSourceDescriptor(dsRegistry, DataSourceDescriptor.generateNewId(driver), driver, connConfig);
        newDS.setName(dsName);
        newDS.setTemporary(true);
        if (savePassword) {
            newDS.setSavePassword(true);
        }
        if (folder != null) {
            newDS.setFolder(folder);
        }
        newDS.setShowSystemObjects(showSystemObjects);
        newDS.setShowUtilityObjects(showUtilityObjects);
        //ds.set
        dsRegistry.addDataSource(newDS);
        return newDS;
    }

}
