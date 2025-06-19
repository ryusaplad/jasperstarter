/*
 * Copyright 2012-2015 Cenote GmbH.
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

import de.cenote.jasperstarter.types.AskFilter;
import de.cenote.jasperstarter.types.Command;
import de.cenote.jasperstarter.types.DsType;
import de.cenote.jasperstarter.types.Dest;
import de.cenote.jasperstarter.types.OutputFormat;
import de.cenote.tools.classpath.ApplicationClasspath;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentGroup;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

/**
 * <p>App class.</p>
 *
 * @author Volker Voßkämper
 * @version $Revision: 349bcea5768c:59 branch:default $
 */
public class App {

    private Namespace namespace = null;
    private Map<String, Argument> allArguments = null;
    private static PrintStream configSink = System.err;
    private static PrintStream debugSink = System.err;
    private static PrintStream errSink = System.err;

    /**
     * <p>main.</p>
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Config config = new Config();
        App app = new App();
        // create the command line parser
        ArgumentParser parser = app.createArgumentParser(config);
        if (args.length == 0) {
            System.out.println(parser.formatUsage());
            System.out.println("type: jasperstarter -h to get help");
            System.exit(0);
        }
        try {
            app.parseArgumentParser(args, parser, config);
        } catch (ArgumentParserException ex) {
            parser.handleError(ex);
            System.exit(1);
        }
        if (config.isVerbose()) {
            configSink.print("Command line:");
            for (String arg : args) {
                configSink.print(" " + arg);
            }
            // @todo: this makes sense only if Config.toString() is overwitten
//            configSink.print("\n");
//            configSink.println(config);
        }

        // @todo: main() will not be executed in tests...
        // setting locale if given
        if (config.hasLocale()) {
            Locale.setDefault(config.getLocale());
        }

        try {
            switch (Command.getCommand(config.getCommand())) {
                case COMPILE:
                case CP:
                    app.compile(config);
                    break;
                case PROCESS:
                case PR:
                    app.processReport(config);
                    break;
                case LIST_PRINTERS:
                case PRINTERS:
                case LPR:
                    app.listPrinters();
                    break;
                case LIST_PARAMETERS:
                case PARAMS:
                case LPA:
                    App.listReportParams(config, new File(config.getInput()).getAbsoluteFile());
                    break;
            }
        } catch (IllegalArgumentException ex) {
            errSink.println(ex.getMessage());
            System.exit(1);
        } catch (InterruptedException ex) {
            errSink.println(ex.getMessage());
            // Restore interrupted state...
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (JRException ex) {
            errSink.println(ex.getMessage());
            System.exit(1);
        }
    }

    private void compile(Config config) {
        IllegalArgumentException error = null;
        File input = new File(config.getInput());
        if (input.isFile()) {
            try {
                Report report = new Report(config, input);
                report.compileToFile();
            } catch (IllegalArgumentException ex) {
                error = ex;
            }
        } else if (input.isDirectory()) {
            // compile all .jrxml files in this directory
            FileFilter fileFilter = new WildcardFileFilter("*.jrxml", IOCase.INSENSITIVE);
            File[] files = input.listFiles(fileFilter);
            for (File file : files) {
                try {
                    System.out.println("Compiling: \"" + file + "\"");
                    Report report = new Report(config, file);
                    report.compileToFile();
                } catch (IllegalArgumentException ex) {
                    error = ex;
                }
            }
        } else {
            error = new IllegalArgumentException("Error: not a file: " + input.getName());
        }
        if (error != null) {
            throw error;
        }
    }

    private void processReport(Config config)
            throws IllegalArgumentException, InterruptedException, JRException {
        // Find the JDBC directory
        File jdbcDir = findJdbcDirectory(config);

        // Now load the JDBC drivers
        loadJdbcDrivers(jdbcDir, config);

        // add optional resources to classpath
        if (config.hasResource()) {
            try {
                if ("".equals(config.getResource())) { // the default
                    // add the parent of input to classpath
                    File res = new File(config.getInput()).getAbsoluteFile().getParentFile();
                    if (res.isDirectory()) {
                        ApplicationClasspath.add(res);
                        if (config.isVerbose()) {
                            configSink.println(
                                    "Added resource \"" + res + "\" to classpath");
                        }
                    } else {
                        throw new IllegalArgumentException(
                                "Resource path \"" + res + "\" is not a directory");
                    }
                } else {
                    // add file or dir to classpath
                    File res = new File(config.getResource());
                    ApplicationClasspath.add(res);
                    if (config.isVerbose()) {
                        configSink.println(
                                "Added resource \"" + res + "\" to classpath");
                    }
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException("Error adding resource \""
                        + config.getResource() + "\" to classpath", ex);
            }
        }
        File inputFile = new File(config.getInput()).getAbsoluteFile();
        if (config.isVerbose()) {
            configSink.println("Original input file: " + inputFile.getAbsolutePath());
        }
        inputFile = locateInputFile(inputFile);
        if (config.isVerbose()) {
            configSink.println("Using input file: " + inputFile.getAbsolutePath());
        }
        Report report = new Report(config, inputFile);

        report.fill();

        List<OutputFormat> formats = config.getOutputFormats();
        Boolean viewIt = false;
        Boolean printIt = false;

        if (formats.size() > 1 && config.getOutput().equals("-")) {
            throw new IllegalArgumentException(
                    "output file \"-\" cannot be used with multiple output formats: " + formats);
        }

        for (OutputFormat f : formats) {
            if (OutputFormat.print.equals(f)) {
                printIt = true;
            } else if (OutputFormat.view.equals(f)) {
                viewIt = true;
            } else if (OutputFormat.pdf.equals(f)) {
                report.exportPdf();
            } else if (OutputFormat.docx.equals(f)) {
                report.exportDocx();
            } else if (OutputFormat.odt.equals(f)) {
                report.exportOdt();
            } else if (OutputFormat.rtf.equals(f)) {
                report.exportRtf();
            } else if (OutputFormat.html.equals(f)) {
                report.exportHtml();
            } else if (OutputFormat.xml.equals(f)) {
                report.exportXml();
            } else if (OutputFormat.xls.equals(f)) {
                report.exportXls();
            } else if (OutputFormat.xlsMeta.equals(f)) {
                report.exportXlsMeta();
            } else if (OutputFormat.xlsx.equals(f)) {
                report.exportXlsx();
            } else if (OutputFormat.csv.equals(f)) {
                report.exportCsv();
            } else if (OutputFormat.csvMeta.equals(f)) {
                report.exportCsvMeta();
            } else if (OutputFormat.ods.equals(f)) {
                report.exportOds();
            } else if (OutputFormat.pptx.equals(f)) {
                report.exportPptx();
            } else if (OutputFormat.xhtml.equals(f)) {
                report.exportXhtml();
            } else if (OutputFormat.jrprint.equals(f)) {
                report.exportJrprint();
            } else {
            	throw new IllegalArgumentException("Error output format \"" + f +  "\" not implemented!");
            }
        }
        if (viewIt) {
            report.view();
        } else if (printIt) {
            // print directly only if viewer is not activated
            report.print();
        }

    }

    /**
     * Finds the JDBC directory to use.
     * 
     * @param config the configuration
     * @return the JDBC directory
     * @throws IllegalArgumentException if no valid JDBC directory could be found
     */
    private File findJdbcDirectory(Config config) throws IllegalArgumentException {
        File jdbcDir = null;
        
        // First check if user specified a JDBC directory
        if (config.hasJdbcDir()) {
            jdbcDir = config.getJdbcDir();
            if (!jdbcDir.exists() || !jdbcDir.isDirectory()) {
                throw new IllegalArgumentException("JDBC directory does not exist or is not a directory: " + jdbcDir.getAbsolutePath());
            }
            if (config.isVerbose()) {
                configSink.println("Using user-specified jdbc-dir: " + jdbcDir.getAbsolutePath());
            }
            return jdbcDir;
        }
        
        // If not specified, try multiple locations - only show paths in verbose mode
        if (config.isVerbose()) {
            configSink.println("Looking for JDBC directory...");
        }
        
        // 1. Try current directory's jdbc folder
        File currentDir = new File(".").getAbsoluteFile();
        if (config.isVerbose()) {
            configSink.println("Current directory: " + currentDir.getAbsolutePath());
        }
        jdbcDir = new File(currentDir, "jdbc");
        if (config.isVerbose()) {
            configSink.println("Checking: " + jdbcDir.getAbsolutePath() + " - " + (jdbcDir.exists() && jdbcDir.isDirectory() ? "FOUND" : "NOT FOUND"));
        }
        if (jdbcDir.exists() && jdbcDir.isDirectory()) {
            if (config.isVerbose()) {
                configSink.println("Using jdbc-dir: " + jdbcDir.getAbsolutePath());
            }
            return jdbcDir;
        }
        
        // 2. Try parent directory's jdbc folder
        jdbcDir = new File(currentDir.getParent(), "jdbc");
        if (config.isVerbose()) {
            configSink.println("Checking: " + jdbcDir.getAbsolutePath() + " - " + (jdbcDir.exists() && jdbcDir.isDirectory() ? "FOUND" : "NOT FOUND"));
        }
        if (jdbcDir.exists() && jdbcDir.isDirectory()) {
            if (config.isVerbose()) {
                configSink.println("Using jdbc-dir: " + jdbcDir.getAbsolutePath());
            }
            return jdbcDir;
        }
        
        // 3. Try to find the JDBC directory relative to the application path
        try {
            File appPath = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            if (config.isVerbose()) {
                configSink.println("Application path: " + appPath.getAbsolutePath());
            }
            
            // If we're in a bin directory, check for jdbc as a sibling
            File appDir = appPath.getParentFile();
            if (appDir != null) {
                if (config.isVerbose()) {
                    configSink.println("Application directory: " + appDir.getAbsolutePath());
                }
                if (appDir.getName().equalsIgnoreCase("bin")) {
                    jdbcDir = new File(appDir.getParent(), "jdbc");
                    if (config.isVerbose()) {
                        configSink.println("Checking: " + jdbcDir.getAbsolutePath() + " - " + (jdbcDir.exists() && jdbcDir.isDirectory() ? "FOUND" : "NOT FOUND"));
                    }
                    if (jdbcDir.exists() && jdbcDir.isDirectory()) {
                        if (config.isVerbose()) {
                            configSink.println("Using jdbc-dir: " + jdbcDir.getAbsolutePath());
                        }
                        return jdbcDir;
                    }
                }
            }
        } catch (URISyntaxException e) {
            if (config.isVerbose()) {
                configSink.println("Error determining application path: " + e.getMessage());
            }
        }
        
        // If we got here, no valid directory was found
        throw new IllegalArgumentException("Could not find JDBC directory. Please specify using --jdbc-dir option.");
    }

    /**
     * Loads JDBC drivers from the specified directory.
     * Tries multiple approaches to handle different Java versions.
     * 
     * @param jdbcDir directory containing JDBC driver JARs
     * @param config configuration for verbose output
     * @throws IllegalArgumentException if drivers cannot be loaded
     */
    private void loadJdbcDrivers(File jdbcDir, Config config) throws IllegalArgumentException {
        if (config.isVerbose()) {
            configSink.println("Loading JDBC drivers from: " + jdbcDir.getAbsolutePath());
        }
        
        // Check if JDBC directory exists and is readable
        if (!jdbcDir.exists()) {
            throw new IllegalArgumentException("JDBC directory does not exist: " + jdbcDir.getAbsolutePath());
        }
        
        if (!jdbcDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + jdbcDir.getAbsolutePath());
        }
        
        if (!jdbcDir.canRead()) {
            throw new IllegalArgumentException("Cannot read JDBC directory: " + jdbcDir.getAbsolutePath());
        }
        
        // List all files in the JDBC directory only in verbose mode
        if (config.isVerbose()) {
            File[] allFiles = jdbcDir.listFiles();
            configSink.println("Files found in JDBC directory:");
            if (allFiles == null || allFiles.length == 0) {
                configSink.println("  No files found in JDBC directory!");
            } else {
                for (File file : allFiles) {
                    configSink.println("  - " + file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }
        
        // Check for specific database driver JARs if using a database connection
        if (config.getDbType() != null && !DsType.none.equals(config.getDbType())) {
            // Verify if required driver files exist
            boolean driverFound = false;

            if (DsType.mysql.equals(config.getDbType())) {
                driverFound = false;
                File[] files = jdbcDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String filename = file.getName().toLowerCase();
                        if (filename.contains("mysql") && filename.endsWith(".jar")) {
                            driverFound = true;
                            if (config.isVerbose()) {
                                configSink.println("Found MySQL driver JAR: " + file.getName());
                            }
                            break;
                        }
                    }
                }
                
                if (!driverFound && config.isVerbose()) {
                    configSink.println("WARNING: No MySQL driver JAR found in " + jdbcDir.getAbsolutePath());
                    configSink.println("         Download MySQL Connector/J from https://dev.mysql.com/downloads/connector/j/");
                    configSink.println("         and place it in the jdbc directory.");
                }
            } else if (DsType.postgres.equals(config.getDbType())) {
                driverFound = false;
                File[] files = jdbcDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String filename = file.getName().toLowerCase();
                        if ((filename.contains("postgresql") || filename.contains("postgres")) && filename.endsWith(".jar")) {
                            driverFound = true;
                            if (config.isVerbose()) {
                                configSink.println("Found PostgreSQL driver JAR: " + file.getName());
                            }
                            break;
                        }
                    }
                }
                
                if (!driverFound && config.isVerbose()) {
                    configSink.println("WARNING: No PostgreSQL driver JAR found in " + jdbcDir.getAbsolutePath());
                    configSink.println("         Download PostgreSQL JDBC Driver from https://jdbc.postgresql.org/");
                    configSink.println("         and place it in the jdbc directory.");
                }
            }
        }

        // First try using ApplicationClasspath (works for Java 8)
        try {
            ApplicationClasspath.addJars(jdbcDir.getAbsolutePath());
            if (config.isVerbose()) {
                configSink.println("Successfully loaded JDBC drivers via system classpath");
            }
            return;
        } catch (IOException e) {
            if (config.isVerbose()) {
                configSink.println("Could not add to system classpath: " + e.getMessage());
                configSink.println("Trying alternative driver loading method");
            }
        }

        // If we get here, try the direct loading method (for Java 9+)
        try {
            loadJdbcDriversDirectly(jdbcDir, config);
            if (config.isVerbose()) {
                configSink.println("Successfully loaded JDBC drivers via direct loading");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load JDBC drivers: " + e.getMessage(), e);
        }
    }

    /**
     *
     * @param inputFile file or basename of a JasperReports file
     * @return a valid file that is not a directory and has a fileending of
     * (jrxml, jasper, jrprint)
     */
    private File locateInputFile(File inputFile) {

        if (!inputFile.exists()) {
            File newInputfile;
            // maybe the user omitted the file extension
            // first trying .jasper
            newInputfile = new File(inputFile.getAbsolutePath() + ".jasper");
            if (newInputfile.isFile()) {
                inputFile = newInputfile;
            }
            if (!inputFile.exists()) {
                // second trying .jrxml
                newInputfile = new File(inputFile.getAbsolutePath() + ".jrxml");
                if (newInputfile.isFile()) {
                    inputFile = newInputfile;
                }
            }
        }
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("Error: file not found: " + inputFile.getAbsolutePath());
        } else if (inputFile.isDirectory()) {
            throw new IllegalArgumentException("Error: " + inputFile.getAbsolutePath() + " is a directory, file needed");
        }

        return inputFile;
    }

    private void listPrinters() {
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        System.out.println("Default printer:");
        System.out.println("-----------------");
        System.out.println((defaultService == null) ? "--- not set ---" : defaultService.getName());
        System.out.println("");
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        System.out.println("Available printers:");
        System.out.println("--------------------");
        for (PrintService service : services) {
            System.out.println(service.getName());
        }
    }

    private ArgumentParser createArgumentParser(Config config) {
        this.allArguments = new HashMap<String, Argument>();

        ArgumentParser parser = ArgumentParsers.newArgumentParser("jasperstarter", false, "-", "@")
                .version(config.getVersionString());

        //ArgumentGroup groupOptions = parser.addArgumentGroup("options");

        parser.addArgument("-h", "--help").action(Arguments.help()).help("show this help message and exit");
        parser.addArgument("--locale").dest(Dest.LOCALE).metavar("<lang>")
                .help("set locale with two-letter ISO-639 code"
                        + " or a combination of ISO-639 and ISO-3166 like de_DE");
        parser.addArgument("-v", "--verbose").dest(Dest.DEBUG).action(Arguments.storeTrue()).help("display additional messages");
        parser.addArgument("-V", "--version").action(Arguments.version()).help("display version information and exit");

        Subparsers subparsers = parser.addSubparsers().title("commands").
                help("type <cmd> -h to get help on command").metavar("<cmd>").
                dest(Dest.COMMAND);

        Subparser parserCompile =
                subparsers.addParser("compile", true).aliases("cp")
                .help("compile reports");
        createCompileArguments(parserCompile);

        Subparser parserProcess =
                subparsers.addParser("process", true).aliases("pr")
                .help("view, print or export an existing report");
        createProcessArguments(parserProcess);

        Subparser parserListPrinters =
                subparsers.addParser("list_printers", true).aliases("printers", "lpr")
                .help("lists available printers");

        Subparser parserListParams =
                subparsers.addParser("list_parameters", true).aliases("params", "lpa").
                help("list parameters from a given report");
        createListParamsArguments(parserListParams);

        return parser;
    }

    private void createCompileArguments(Subparser parser) {
        ArgumentGroup groupOptions = parser.addArgumentGroup("options");
        groupOptions.addArgument("input").metavar("<input>").dest(Dest.INPUT).required(true).help("input file (.jrxml) or directory");
        groupOptions.addArgument("-o").metavar("<output>").dest(Dest.OUTPUT).help("directory or basename of outputfile(s)");
    }

    private void createListParamsArguments(Subparser parser) {
        ArgumentGroup groupOptions = parser.addArgumentGroup("options");
        groupOptions.addArgument("input").metavar("<input>").dest(Dest.INPUT).required(true).help("input file (.jrxml) or (.jasper)");
    }

    private void createProcessArguments(Subparser parser) {
        ArgumentGroup groupOptions = parser.addArgumentGroup("options");
        groupOptions.addArgument("-f").metavar("<fmt>").dest(Dest.OUTPUT_FORMATS).
                required(true).nargs("+").type(Arguments.enumType(OutputFormat.class)).
                help("view, print, pdf, rtf, xls, xlsMeta, xlsx, docx, odt, ods, pptx, csv, csvMeta, html, xhtml, xml, jrprint");
        groupOptions.addArgument("input").metavar("<input>").dest(Dest.INPUT).required(true).help("input file (.jrxml|.jasper|.jrprint)");
        groupOptions.addArgument("-o").
                metavar("<output>").
                dest(Dest.OUTPUT).
                help("directory or basename of outputfile(s), use '-' for stdout");
        //groupOptions.addArgument("-h", "--help").action(Arguments.help()).help("show this help message and exit");

        ArgumentGroup groupCompileOptions = parser.addArgumentGroup("compile options");
        groupCompileOptions.addArgument("-w", "--write-jasper").
                dest(Dest.WRITE_JASPER).action(Arguments.storeTrue()).help("write .jasper file to input dir if jrxml is processed");

        ArgumentGroup groupFillOptions = parser.addArgumentGroup("fill options");
        groupFillOptions.addArgument("-a").metavar("<filter>").dest(Dest.ASK)
                .type(Arguments.enumType(AskFilter.class)).nargs("?")
                .setConst(AskFilter.p)
                .help("ask for report parameters. Filter: a, ae, u, ue, p, pe"
                        + " (see usage)");
        groupFillOptions.addArgument("-P").metavar("<param>").dest(Dest.PARAMS)
                .nargs("+").help(
                        "report parameter: name=value [...]");
        groupFillOptions.addArgument("-r").metavar("<resource>").dest(Dest.RESOURCE)
                .nargs("?").setConst("").help(
                        "path to report resource dir or jar file. If <resource> is not"
                                + " given the input directory is used.");

        ArgumentGroup groupDatasourceOptions = parser.addArgumentGroup("datasource options");
        groupDatasourceOptions.addArgument("-t").metavar("<dstype>").dest(Dest.DS_TYPE).
                required(false).type(Arguments.enumType(DsType.class)).setDefault(DsType.none).
                help("datasource type: none, csv, xml, json, jsonql, mysql, postgres, oracle, generic (jdbc)");
        Argument argDbHost = groupDatasourceOptions.addArgument("-H").metavar("<dbhost>").dest(Dest.DB_HOST).help("database host");
        Argument argDbUser = groupDatasourceOptions.addArgument("-u").metavar("<dbuser>").dest(Dest.DB_USER).help("database user");
        Argument argDbPasswd = groupDatasourceOptions.addArgument("-p").metavar("<dbpasswd>").dest(Dest.DB_PASSWD).setDefault("").help("database password");
        Argument argDbName = groupDatasourceOptions.addArgument("-n").metavar("<dbname>").dest(Dest.DB_NAME).help("database name");
        Argument argDbSid = groupDatasourceOptions.addArgument("--db-sid").metavar("<sid>").dest(Dest.DB_SID).help("oracle sid");
        Argument argDbPort = groupDatasourceOptions.addArgument("--db-port").metavar("<port>").dest(Dest.DB_PORT).type(Integer.class).help("database port");
        Argument argDbDriver = groupDatasourceOptions.addArgument("--db-driver").metavar("<name>").dest(Dest.DB_DRIVER).help("jdbc driver class name for use with type: generic");
        Argument argDbUrl = groupDatasourceOptions.addArgument("--db-url").metavar("<jdbcUrl>").dest(Dest.DB_URL).help("jdbc url without user, passwd with type:generic");
        groupDatasourceOptions.addArgument("--jdbc-dir").metavar("<dir>").dest(Dest.JDBC_DIR).type(File.class).help("directory where jdbc driver jars are located. Defaults to ./jdbc");
        Argument argDataFile = groupDatasourceOptions.addArgument("--data-file").
            metavar("<file>").
            dest(Dest.DATA_FILE).
            type(Arguments.fileType().acceptSystemIn().verifyCanRead()).
            help("input file for file based datasource, use '-' for stdin");
        groupDatasourceOptions.addArgument("--csv-first-row").metavar("true", "false").dest(Dest.CSV_FIRST_ROW).action(Arguments.storeTrue()).help("first row contains column headers");
        Argument argCsvColumns = groupDatasourceOptions.addArgument("--csv-columns").metavar("<list>").dest(Dest.CSV_COLUMNS).help("Comma separated list of column names");
        groupDatasourceOptions.addArgument("--csv-record-del").metavar("<delimiter>").dest(Dest.CSV_RECORD_DEL).setDefault(System.getProperty("line.separator")).help("CSV Record Delimiter - defaults to line.separator");
        groupDatasourceOptions.addArgument("--csv-field-del").metavar("<delimiter>").dest(Dest.CSV_FIELD_DEL).setDefault(",").help("CSV Field Delimiter - defaults to \",\"");
        groupDatasourceOptions.addArgument("--csv-charset").metavar("<charset>").dest(Dest.CSV_CHARSET).setDefault("utf-8").help("CSV charset - defaults to \"utf-8\"");
        Argument argXmlXpath = groupDatasourceOptions.addArgument("--xml-xpath").metavar("<xpath>").dest(Dest.XML_XPATH).help("XPath for XML Datasource");
        Argument argJsonQuery = groupDatasourceOptions.addArgument("--json-query").metavar("<jsonquery>").dest(Dest.JSON_QUERY).help("JSON query string for JSON Datasource");
        Argument argJsonQLQuery = groupDatasourceOptions.addArgument("--jsonql-query").metavar("<jsonqlquery>").dest(Dest.JSONQL_QUERY).help("JSONQL query string for JSONQL Datasource");

        ArgumentGroup groupOutputOptions = parser.addArgumentGroup("output options");
        groupOutputOptions.addArgument("-N").metavar("<printername>").dest(Dest.PRINTER_NAME).help("name of printer");
        groupOutputOptions.addArgument("-d").dest(Dest.WITH_PRINT_DIALOG).action(Arguments.storeTrue()).help("show print dialog when printing");
        groupOutputOptions.addArgument("-s").metavar("<reportname>").dest(Dest.REPORT_NAME).help("set internal report/document name when printing");
        groupOutputOptions.addArgument("-c").metavar("<copies>").dest(Dest.COPIES)
                .type(Integer.class).choices(Arguments.range(1, Integer.MAX_VALUE))
                .help("number of copies. Defaults to 1");
        groupOutputOptions.addArgument("--out-field-del").metavar("<delimiter>").dest(Dest.OUT_FIELD_DEL).setDefault(",").help("Export CSV (Metadata) Field Delimiter - defaults to \",\"");
        groupOutputOptions.addArgument("--out-charset").metavar("<charset>").dest(Dest.OUT_CHARSET).setDefault("utf-8").help("Export CSV (Metadata) Charset - defaults to \"utf-8\"");

        allArguments.put(argDbHost.getDest(), argDbHost);
        allArguments.put(argDbUser.getDest(), argDbUser);
        allArguments.put(argDbPasswd.getDest(), argDbPasswd);
        allArguments.put(argDbName.getDest(), argDbName);
        allArguments.put(argDbSid.getDest(), argDbSid);
        allArguments.put(argDbPort.getDest(), argDbPort);
        allArguments.put(argDbDriver.getDest(), argDbDriver);
        allArguments.put(argDbUrl.getDest(), argDbUrl);
        allArguments.put(argDataFile.getDest(), argDataFile);
        allArguments.put(argCsvColumns.getDest(), argCsvColumns);
        allArguments.put(argXmlXpath.getDest(), argXmlXpath);
        allArguments.put(argJsonQuery.getDest(), argJsonQuery);
        allArguments.put(argJsonQLQuery.getDest(), argJsonQLQuery);
    }

    private void parseArgumentParser(String[] args, ArgumentParser parser, Config config) throws ArgumentParserException {
        parser.parseArgs(args, config);
        // change some arguments to required depending on db-type
        if (config.hasDbType()) {
            if (config.getDbType().equals(DsType.none)) {
                // nothing to do here
            } else if (config.getDbType().equals(DsType.mysql)) {
                allArguments.get(Dest.DB_HOST).required(true);
                allArguments.get(Dest.DB_USER).required(true);
                allArguments.get(Dest.DB_NAME).required(true);
                allArguments.get(Dest.DB_PORT).setDefault(DsType.mysql.getPort());
            } else if (config.getDbType().equals(DsType.postgres)) {
                allArguments.get(Dest.DB_HOST).required(true);
                allArguments.get(Dest.DB_USER).required(true);
                allArguments.get(Dest.DB_NAME).required(true);
                allArguments.get(Dest.DB_PORT).setDefault(DsType.postgres.getPort());
            } else if (config.getDbType().equals(DsType.oracle)) {
                allArguments.get(Dest.DB_HOST).required(true);
                allArguments.get(Dest.DB_USER).required(true);
                allArguments.get(Dest.DB_PASSWD).required(true);
                allArguments.get(Dest.DB_SID).required(true);
                allArguments.get(Dest.DB_PORT).setDefault(DsType.oracle.getPort());
            } else if (config.getDbType().equals(DsType.generic)) {
                allArguments.get(Dest.DB_DRIVER).required(true);
                allArguments.get(Dest.DB_URL).required(true);
            } else if (DsType.csv.equals(config.getDbType())) {
                allArguments.get(Dest.DATA_FILE).required(true);
                if (!config.getCsvFirstRow()) {
                    allArguments.get(Dest.CSV_COLUMNS).required(true);
                }
            } else if (DsType.xml.equals(config.getDbType())) {
                allArguments.get(Dest.DATA_FILE).required(true);
            } else if (DsType.json.equals(config.getDbType())) {
                allArguments.get(Dest.DATA_FILE).required(true);
            } else if (DsType.jsonql.equals(config.getDbType())) {
                allArguments.get(Dest.DATA_FILE).required(true);
            }
        }
        // parse again so changed arguments become effectiv
        parser.parseArgs(args, config);
    }

    /**
     * <p>listReportParams.</p>
     *
     * @param config a {@link de.cenote.jasperstarter.Config} object.
     * @param input a {@link java.io.File} object.
     * @throws java.lang.IllegalArgumentException if any.
     */
    public static void listReportParams(Config config, File input) throws IllegalArgumentException {
        boolean all;
        Report report = new Report(config, input);
        JRParameter[] params = report.getReportParameters();
        int maxName = 1;
        int maxClassName = 1;
        int maxDesc = 1;
        all = false; // this is a default for now
        // determine proper length of stings for nice alignment
        for (JRParameter param : params) {
            if (!param.isSystemDefined() || all) {
                if (param.getName() != null) {
                    maxName = Math.max(maxName, param.getName().length());
                }
                if (param.getValueClassName() != null) {
                    maxClassName = Math.max(maxClassName, param.getValueClassName().length());
                }
                if (param.getDescription() != null) {
                    maxDesc = Math.max(maxDesc, param.getDescription().length());
                }
            }
        }
        for (JRParameter param : params) {
            if (!param.isSystemDefined() || all) {
                System.out.printf("%s %-" + maxName + "s %-" + maxClassName + "s %-" + maxDesc + "s %n",
                        //(param.isSystemDefined() ? "S" : "U"),
                        (param.isForPrompting() ? "P" : "N"),
                        param.getName(),
                        param.getValueClassName(),
                        (param.getDescription() != null ? param.getDescription() : ""));
            }
        }
    }

    /**
     * Loads JDBC drivers directly for Java 9+ compatibility without modifying the
     * classpath.
     * This method attempts to load .jar files in the specified directory directly.
     * 
     * @param jdbcDir The directory containing JDBC driver JARs
     * @param config  Configuration for verbose output
     */
    private void loadJdbcDriversDirectly(File jdbcDir, Config config) {
        configSink.println("Attempting direct JAR loading from " + jdbcDir.getAbsolutePath());

        if (!jdbcDir.isDirectory()) {
            configSink.println("ERROR: JDBC directory is not a directory: " + jdbcDir.getAbsolutePath());
            return;
        }

        File[] jarFiles = jdbcDir.listFiles(file -> file.getName().toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            configSink.println("No JAR files found in JDBC directory: " + jdbcDir.getAbsolutePath());
            return;
        }

        // Display class path information
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        configSink.println("System ClassLoader type: " + systemClassLoader.getClass().getName());
        configSink.println(
                "Current Thread ClassLoader: " + Thread.currentThread().getContextClassLoader().getClass().getName());

        try {
            configSink.println("Java version: " + System.getProperty("java.version"));
            configSink.println("Java vendor: " + System.getProperty("java.vendor"));
        } catch (Exception e) {
            configSink.println("Could not retrieve Java properties");
        }

        for (File jarFile : jarFiles) {
            try {
                configSink.println("Attempting to load JAR: " + jarFile.getAbsolutePath());

                // Try using URLClassLoader directly
                URL jarUrl = jarFile.toURI().toURL();
                URLClassLoader childClassLoader = new URLClassLoader(
                        new URL[] { jarUrl },
                        Thread.currentThread().getContextClassLoader());
                Thread.currentThread().setContextClassLoader(childClassLoader);

                configSink.println("Successfully created ClassLoader for: " + jarFile.getAbsolutePath());

                // Try to automatically register database drivers based on JAR name
                String jarName = jarFile.getName().toLowerCase();

                if (jarName.contains("mysql")) {
                    configSink.println("Detected MySQL JAR: " + jarFile.getName());

                    // Try legacy driver class
                    try {
                        String driverClass = "com.mysql.jdbc.Driver";
                        configSink.println("Attempting to load driver class: " + driverClass);
                        Class<?> driver = Class.forName(driverClass, true, childClassLoader);
                        configSink.println("Successfully loaded MySQL JDBC driver class: " + driverClass);

                        // Try to create an instance
                        try {
                            Object driverInstance = driver.getDeclaredConstructor().newInstance();
                            configSink.println("Successfully created driver instance: " + driverInstance);
                        } catch (Exception e) {
                            configSink.println("Could not instantiate driver: " + e.getMessage());
                        }
                    } catch (ClassNotFoundException e) {
                        configSink.println("Failed to load legacy MySQL driver: " + e.getMessage());

                        // Try newer MySQL driver class
                        try {
                            String driverClass = "com.mysql.cj.jdbc.Driver";
                            configSink.println("Attempting to load driver class: " + driverClass);
                            Class<?> driver = Class.forName(driverClass, true, childClassLoader);
                            configSink.println("Successfully loaded MySQL CJ JDBC driver class: " + driverClass);

                            // Try to create an instance
                            try {
                                Object driverInstance = driver.getDeclaredConstructor().newInstance();
                                configSink.println("Successfully created driver instance: " + driverInstance);
                            } catch (Exception ex) {
                                configSink.println("Could not instantiate driver: " + ex.getMessage());
                            }
                        } catch (ClassNotFoundException ex) {
                            configSink.println("Failed to load newer MySQL driver: " + ex.getMessage());
                        }
                    }
                } else if (jarName.contains("postgresql") || jarName.contains("postgres")) {
                    configSink.println("Detected PostgreSQL JAR: " + jarFile.getName());

                    try {
                        String driverClass = "org.postgresql.Driver";
                        configSink.println("Attempting to load driver class: " + driverClass);
                        Class<?> driver = Class.forName(driverClass, true, childClassLoader);
                        configSink.println("Successfully loaded PostgreSQL JDBC driver class: " + driverClass);
                    } catch (ClassNotFoundException e) {
                        configSink.println("Failed to load PostgreSQL driver: " + e.getMessage());
                    }
                } else if (jarName.contains("oracle")) {
                    configSink.println("Detected Oracle JAR: " + jarFile.getName());

                    try {
                        String driverClass = "oracle.jdbc.driver.OracleDriver";
                        configSink.println("Attempting to load driver class: " + driverClass);
                        Class<?> driver = Class.forName(driverClass, true, childClassLoader);
                        configSink.println("Successfully loaded Oracle JDBC driver class: " + driverClass);
                    } catch (ClassNotFoundException e) {
                        configSink.println("Failed to load Oracle driver: " + e.getMessage());
                    }
                } else {
                    configSink.println(
                            "Unknown JAR type, not attempting specific driver class loading: " + jarFile.getName());
                }
            } catch (Exception e) {
                configSink.println("Failed to load JAR file: " + jarFile.getAbsolutePath());
                configSink.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            }
        }
    }
}
