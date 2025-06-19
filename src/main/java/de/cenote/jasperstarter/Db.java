/*
 * Copyright 2012 Cenote GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cenote.jasperstarter;

import de.cenote.jasperstarter.types.DsType;

import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.data.JsonQLDataSource;

import org.apache.commons.lang.StringEscapeUtils;

/**
 * <p>Db class.</p>
 *
 * @author Volker Voßkämper
 * @version $Revision: 5b92831f1a80:54 branch:default $
 */
public class Db {
    private static PrintStream configSink = System.err;
    private static PrintStream debugSink = System.err;

    /**
     * <p>Constructor for Db.</p>
     */
    public Db() {
        //
        // In normal usage, the static initialisation of configSink and
        // debugSink is fine. However, when running tests, these are
        // modified at run-time, so make sure we get the current version!
        //
        configSink = System.err;
        debugSink = System.err;
    }

    /**
     * <p>getCsvDataSource.</p>
     *
     * @param config a {@link de.cenote.jasperstarter.Config} object.
     * @return a {@link net.sf.jasperreports.engine.data.JRCsvDataSource} object.
     * @throws net.sf.jasperreports.engine.JRException if any.
     */
    public JRCsvDataSource getCsvDataSource(Config config) throws JRException {
        JRCsvDataSource ds;
        try {
            ds = new JRCsvDataSource(config.getDataFileInputStream(), config.csvCharset);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException("Unknown CSV charset: "
                    + config.csvCharset
                    + ex.getMessage(), ex);
        }

        ds.setUseFirstRowAsHeader(config.getCsvFirstRow());
        if (!config.getCsvFirstRow()) {
            ds.setColumnNames(config.getCsvColumns());
        }

        ds.setRecordDelimiter(
                StringEscapeUtils.unescapeJava(config.getCsvRecordDel()));
        ds.setFieldDelimiter(config.getCsvFieldDel());

        if (config.isVerbose()) {
            configSink.println("Use first row: " + config.getCsvFirstRow());
            configSink.print("CSV Columns:");
            for (String name : config.getCsvColumns()) {
                configSink.print(" " + name);
            }
            configSink.println("");
            configSink.println("-----------------------");
            configSink.println("Record delimiter literal: " + config.getCsvRecordDel());
            configSink.println("Record delimiter: " + ds.getRecordDelimiter());
            configSink.println("Field delimiter: " + ds.getFieldDelimiter());
            configSink.println("-----------------------");
        }

        return ds;
    }
    
	/**
	 * <p>getXmlDataSource.</p>
	 *
	 * @param config a {@link de.cenote.jasperstarter.Config} object.
	 * @return a {@link net.sf.jasperreports.engine.data.JRXmlDataSource} object.
	 * @throws net.sf.jasperreports.engine.JRException if any.
	 */
	public JRXmlDataSource getXmlDataSource(Config config) throws JRException {
		JRXmlDataSource ds;
		ds = new JRXmlDataSource(config.getDataFileInputStream(), config.xmlXpath);
		if (config.isVerbose()) {
			System.out.println("Data file: " + config.getDataFileName());
			System.out.println("XML xpath: " + config.xmlXpath);
		}
		return ds;
	}

    /**
     * <p>getJsonDataSource.</p>
     *
     * @param config a {@link de.cenote.jasperstarter.Config} object.
     * @return a {@link net.sf.jasperreports.engine.data.JsonDataSource} object.
     * @throws net.sf.jasperreports.engine.JRException if any.
     */
    public JsonDataSource getJsonDataSource(Config config) throws JRException {
		JsonDataSource ds;
		ds = new JsonDataSource(config.getDataFileInputStream(), config.jsonQuery);
		if (config.isVerbose()) {
			System.out.println("Data file: " + config.getDataFileName());
			System.out.println("JSON query : " + config.jsonQuery);
		}
		return ds;
	}

    /**
     * <p>getJsonQLDataSource.</p>
     *
     * @param config a {@link de.cenote.jasperstarter.Config} object.
     * @return a {@link net.sf.jasperreports.engine.data.JsonQLDataSource} object.
     * @throws net.sf.jasperreports.engine.JRException if any.
     */
    public JsonQLDataSource getJsonQLDataSource(Config config) throws JRException {
		JsonQLDataSource ds;
		ds = new JsonQLDataSource(config.getDataFileInputStream(), config.jsonQLQuery);
		if (config.isVerbose()) {
			System.out.println("Data file: " + config.getDataFileName());
			System.out.println("JSONQL query : " + config.jsonQLQuery);
		}
		return ds;
	}

    /**
     * <p>getConnection.</p>
     *
     * @param config a {@link de.cenote.jasperstarter.Config} object.
     * @return a {@link java.sql.Connection} object.
     * @throws java.lang.ClassNotFoundException if any.
     * @throws java.sql.SQLException if any.
     */
    public Connection getConnection(Config config) throws ClassNotFoundException, SQLException {
        Connection conn = null;
        DsType dbtype = config.getDbType();
        String host = config.getDbHost();
        String user = config.getDbUser();
        String passwd = config.getDbPasswd();
        String driver = null;
        String dbname = null;
        String port = null;
        String sid = null;
        String connectString = null;
        if (DsType.mysql.equals(dbtype)) {
            driver = DsType.mysql.getDriver();
            port = config.getDbPort().toString();
            dbname = config.getDbName();
            connectString = "jdbc:mysql://" + host + ":" + port + "/" + dbname + "?useSSL=false&allowPublicKeyRetrieval=true";
        } else if (DsType.postgres.equals(dbtype)) {
            driver = DsType.postgres.getDriver();
            port = config.getDbPort().toString();
            dbname = config.getDbName();
            connectString = "jdbc:postgresql://" + host + ":" + port + "/" + dbname;
        } else if (DsType.oracle.equals(dbtype)) {
            driver = DsType.oracle.getDriver();
            port = config.getDbPort().toString();
            sid = config.getDbSid();
            connectString = "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid;
        } else if (DsType.generic.equals(dbtype)) {
            driver = config.getDbDriver();
            connectString = config.getDbUrl();
        }
        if (config.isVerbose()) {
            configSink.println("JDBC driver: " + driver);
            configSink.println("Connectstring: " + connectString);
            configSink.println("db-user: " + user);
            if (passwd.isEmpty()) {
                configSink.println("db-password is empty");
            }
        }

        // Try loading the driver class
        if (DsType.mysql.equals(dbtype)) {
            // For MySQL, try both the old and new driver class names in the appropriate order
            
            // First, try to find an installed MySQL connector and detect its version
            String legacyDriver = "com.mysql.jdbc.Driver";  // Used in MySQL Connector/J 5.1.x and earlier
            String newDriver = "com.mysql.cj.jdbc.Driver";  // Used in MySQL Connector/J 8.0.x and later
            
            // First try the MySQL 5.1 driver (it's most likely with older applications)
            try {
                if (config.isVerbose()) {
                    configSink.println("Trying legacy MySQL driver: " + legacyDriver);
                }
                Class.forName(legacyDriver);
                if (config.isVerbose()) {
                    configSink.println("Successfully loaded legacy MySQL driver: " + legacyDriver);
                }
            } catch (ClassNotFoundException e1) {
                // If the legacy driver fails, try the newer driver 
                try {
                    if (config.isVerbose()) {
                        configSink.println("Trying newer MySQL driver: " + newDriver);
                    }
                    Class.forName(newDriver);
                    if (config.isVerbose()) {
                        configSink.println("Successfully loaded newer MySQL driver: " + newDriver);
                    }
                } catch (ClassNotFoundException e2) {
                    // As a last resort, try the driver specified in the configuration
                    try {
                        if (config.isVerbose()) {
                            configSink.println("Trying configured MySQL driver: " + driver);
                        }
                        Class.forName(driver);
                        if (config.isVerbose()) {
                            configSink.println("Successfully loaded configured MySQL driver: " + driver);
                        }
                    } catch (ClassNotFoundException e3) {
                        // One final attempt: try the direct JAR loading approach for MySQL
                        try {
                            conn = getMySQLConnectionDirectly(config, connectString, user, passwd);
                            // Return early if we got a connection
                            if (conn != null) {
                                return conn;
                            }
                        } catch (Exception e4) {
                            if (config.isVerbose()) {
                                configSink.println("Direct connection approach failed: " + e4.getMessage());
                            }
                            // If direct approach fails, try the original MySQL driver loading
                            try {
                                loadMySQLDriverDirectly(config);
                                if (config.isVerbose()) {
                                    configSink.println("Successfully loaded MySQL driver using direct JAR loading");
                                }
                            } catch (Exception e5) {
                                // If all attempts fail, provide a helpful error message
                                if (config.isVerbose()) {
                                    configSink.println("All MySQL driver loading approaches failed. Please make sure you have a valid MySQL connector JAR in your jdbc directory.");
                                }
                                throw new ClassNotFoundException(
                                    "Could not load any MySQL JDBC driver. " +
                                    "Tried the following classes: " +
                                    legacyDriver + ", " + 
                                    newDriver + ", " + 
                                    driver + ". " +
                                    "Make sure the correct MySQL connector JAR is in the jdbc directory.");
                            }
                        }
                    }
                }
            }
        } else {
            // For non-MySQL databases, use the standard approach
            Class.forName(driver);
        }
        
        conn = DriverManager.getConnection(connectString, user, passwd);

        return conn;
    }
    
    /**
     * Attempt to manually load the MySQL driver using a direct approach
     * that bypasses normal classloader mechanisms.
     * 
     * @param config The application configuration
     * @throws Exception If the driver cannot be loaded
     */
    private void loadMySQLDriverDirectly(Config config) throws Exception {
        if (config.isVerbose()) {
            configSink.println("Attempting to load MySQL driver using direct JAR loading");
        }
        
        File jdbcDir = null;
        if (config.hasJdbcDir()) {
            jdbcDir = config.getJdbcDir();
        } else {
            // Try to find JDBC directory in common locations
            File currentDir = new File(".").getAbsoluteFile();
            File possibleJdbcDir = new File(currentDir, "jdbc");
            if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                jdbcDir = possibleJdbcDir;
            } else {
                possibleJdbcDir = new File(currentDir.getParent(), "jdbc");
                if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                    jdbcDir = possibleJdbcDir;
                } else {
                    try {
                        File appPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                        File appDir = appPath.getParentFile();
                        if (appDir != null && appDir.getName().equalsIgnoreCase("bin")) {
                            possibleJdbcDir = new File(appDir.getParent(), "jdbc");
                            if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                                jdbcDir = possibleJdbcDir;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore and throw error below
                    }
                }
            }
        }
        
        if (jdbcDir == null || !jdbcDir.exists() || !jdbcDir.isDirectory()) {
            throw new Exception("MySQL JDBC driver JAR not found - JDBC directory not found");
        }
        
        // Find the MySQL driver JAR in the JDBC directory
        File mysqlJarFile = null;
        File[] files = jdbcDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName().toLowerCase();
                if (filename.contains("mysql") && filename.endsWith(".jar")) {
                    mysqlJarFile = file;
                    break;
                }
            }
        }
        
        if (mysqlJarFile == null) {
            throw new Exception("MySQL JDBC driver JAR not found in " + jdbcDir.getAbsolutePath());
        }
        
        if (config.isVerbose()) {
            configSink.println("Found MySQL driver JAR: " + mysqlJarFile.getAbsolutePath());
        }
        
        // Create a URLClassLoader specifically for the MySQL driver JAR
        try {
            URL jarUrl = mysqlJarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[] { jarUrl }, getClass().getClassLoader());
            
            // Try the legacy driver class first
            try {
                Class<?> driverClass = classLoader.loadClass("com.mysql.jdbc.Driver");
                Object driver = driverClass.getDeclaredConstructor().newInstance();
                if (config.isVerbose()) {
                    configSink.println("Successfully loaded legacy MySQL driver");
                }
            } catch (ClassNotFoundException e) {
                // Try the newer driver class
                try {
                    Class<?> driverClass = classLoader.loadClass("com.mysql.cj.jdbc.Driver");
                    Object driver = driverClass.getDeclaredConstructor().newInstance();
                    if (config.isVerbose()) {
                        configSink.println("Successfully loaded newer MySQL driver");
                    }
                } catch (ClassNotFoundException ex) {
                    throw new Exception("Could not load MySQL driver class from " + mysqlJarFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            throw new Exception("Error loading MySQL driver: " + e.getMessage(), e);
        }
    }

    /**
     * Attempt to directly create a MySQL connection without relying on DriverManager
     * 
     * @param config The application configuration
     * @param connectString The JDBC connection string
     * @param user Username for database
     * @param passwd Password for database
     * @return A database connection if successful, null otherwise
     */
    private Connection getMySQLConnectionDirectly(Config config, String connectString, String user, String passwd) {
        if (config.isVerbose()) {
            configSink.println("Attempting to create MySQL connection directly without DriverManager");
            configSink.println("Connect string: " + connectString);
        }
        
        Connection conn = null;
        File jdbcDir = null;
        
        try {
            if (config.hasJdbcDir()) {
                jdbcDir = config.getJdbcDir();
            } else {
                // Find JDBC directory using the same logic as loadMySQLDriverDirectly
                File currentDir = new File(".").getAbsoluteFile();
                File possibleJdbcDir = new File(currentDir, "jdbc");
                if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                    jdbcDir = possibleJdbcDir;
                } else {
                    possibleJdbcDir = new File(currentDir.getParent(), "jdbc");
                    if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                        jdbcDir = possibleJdbcDir;
                    } else {
                        try {
                            File appPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                            File appDir = appPath.getParentFile();
                            if (appDir != null && appDir.getName().equalsIgnoreCase("bin")) {
                                possibleJdbcDir = new File(appDir.getParent(), "jdbc");
                                if (possibleJdbcDir.exists() && possibleJdbcDir.isDirectory()) {
                                    jdbcDir = possibleJdbcDir;
                                }
                            }
                        } catch (Exception e) {
                            // Ignore, will handle below
                        }
                    }
                }
            }
            
            if (jdbcDir == null || !jdbcDir.exists() || !jdbcDir.isDirectory()) {
                if (config.isVerbose()) {
                    configSink.println("Could not find JDBC directory");
                }
                return null;
            }
            
            // Find MySQL connector JAR
            File mysqlJarFile = null;
            File[] files = jdbcDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName().toLowerCase();
                    if (filename.contains("mysql") && filename.endsWith(".jar")) {
                        mysqlJarFile = file;
                        break;
                    }
                }
            }
            
            if (mysqlJarFile == null) {
                if (config.isVerbose()) {
                    configSink.println("MySQL JDBC driver JAR not found in " + jdbcDir.getAbsolutePath());
                }
                return null;
            }
            
            if (config.isVerbose()) {
                configSink.println("Found MySQL driver JAR: " + mysqlJarFile.getAbsolutePath());
            }
            
            // Special handling for MySQL 5.1.x driver
            URL jarUrl = mysqlJarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{ jarUrl }, getClass().getClassLoader());
            
            // Try to load the driver class directly
            try {
                Class<?> driverClass = classLoader.loadClass("com.mysql.jdbc.Driver");
                Object driverInstance = driverClass.getDeclaredConstructor().newInstance();
                
                // Now use Java reflection to connect directly
                Class<?> nonRegisteringDriverClass = classLoader.loadClass("com.mysql.jdbc.NonRegisteringDriver");
                Object nonRegisteringDriver = nonRegisteringDriverClass.getDeclaredConstructor().newInstance();
                
                java.lang.reflect.Method connectMethod = nonRegisteringDriverClass.getMethod("connect", String.class, java.util.Properties.class);
                
                java.util.Properties props = new java.util.Properties();
                props.setProperty("user", user);
                props.setProperty("password", passwd);
                
                conn = (Connection) connectMethod.invoke(nonRegisteringDriver, connectString, props);
                
                if (conn != null) {
                    if (config.isVerbose()) {
                        configSink.println("Successfully created direct MySQL connection using legacy driver");
                    }
                    return conn;
                }
            } catch (Exception e) {
                if (config.isVerbose()) {
                    configSink.println("Failed to create connection with legacy driver: " + e.getMessage());
                }
                
                // Try the newer connector approach
                try {
                    Class<?> driverClass = classLoader.loadClass("com.mysql.cj.jdbc.Driver");
                    Object driverInstance = driverClass.getDeclaredConstructor().newInstance();
                    
                    // Now use Java reflection to connect directly 
                    Class<?> nonRegisteringDriverClass = classLoader.loadClass("com.mysql.cj.jdbc.NonRegisteringDriver");
                    Object nonRegisteringDriver = nonRegisteringDriverClass.getDeclaredConstructor().newInstance();
                    
                    java.lang.reflect.Method connectMethod = nonRegisteringDriverClass.getMethod("connect", String.class, java.util.Properties.class);
                    
                    java.util.Properties props = new java.util.Properties();
                    props.setProperty("user", user);
                    props.setProperty("password", passwd);
                    
                    conn = (Connection) connectMethod.invoke(nonRegisteringDriver, connectString, props);
                    
                    if (conn != null) {
                        if (config.isVerbose()) {
                            configSink.println("Successfully created direct MySQL connection using newer driver");
                        }
                        return conn;
                    }
                } catch (Exception ex) {
                    if (config.isVerbose()) {
                        configSink.println("Failed to create connection with newer driver: " + ex.getMessage());
                    }
                    // Will return null if this fails
                }
            }
        } catch (Exception e) {
            if (config.isVerbose()) {
                configSink.println("Exception in direct connection method: " + e.getMessage());
            }
        }
        
        return conn;
    }
}
