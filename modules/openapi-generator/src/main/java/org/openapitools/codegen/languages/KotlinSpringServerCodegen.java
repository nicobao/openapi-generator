/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
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

package org.openapitools.codegen.languages;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.features.BeanValidationFeatures;
import org.openapitools.codegen.utils.URLPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class KotlinSpringServerCodegen extends AbstractKotlinCodegen
        implements BeanValidationFeatures {

    private static Logger LOGGER =
            LoggerFactory.getLogger(KotlinSpringServerCodegen.class);

    private static final HashSet<String> VARIABLE_RESERVED_WORDS =
            new HashSet<>(Arrays.asList(
                    "ApiClient",
                    "ApiException",
                    "ApiResponse"
            ));

    public static final String TITLE = "title";
    public static final String LAMBDA = "lambda";
    public static final String SERVER_PORT = "serverPort";
    public static final String BASE_PACKAGE = "basePackage";
    public static final String SPRING_BOOT = "spring-boot";
    public static final String EXCEPTION_HANDLER = "exceptionHandler";
    public static final String GRADLE_BUILD_FILE = "gradleBuildFile";
    public static final String SWAGGER_ANNOTATIONS = "swaggerAnnotations";
    public static final String SERVICE_INTERFACE = "serviceInterface";
    public static final String SERVICE_IMPLEMENTATION = "serviceImplementation";

    private String basePackage;
    private String invokerPackage;
    private String serverPort = "8080";
    private String title = "OpenAPI Kotlin Spring";
    private String resourceFolder = "src/main/resources";
    private boolean useBeanValidation = true;
    private boolean exceptionHandler = true;
    private boolean gradleBuildFile = true;
    private boolean swaggerAnnotations = false;
    private boolean serviceInterface = false;
    private boolean serviceImplementation = false;

    public KotlinSpringServerCodegen() {
        super();

        reservedWords.addAll(VARIABLE_RESERVED_WORDS);

        outputFolder = "generated-code/kotlin-spring";
        apiTestTemplateFiles.clear(); // TODO: add test template
        embeddedTemplateDir = templateDir = "kotlin-spring";

        artifactId = "openapi-spring";
        basePackage = invokerPackage = "org.openapitools";
        apiPackage = "org.openapitools.api";
        modelPackage = "org.openapitools.model";

        // Use lists instead of arrays
        typeMapping.put("array", "List");
        typeMapping.put("string", "String");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("integer", "Int");
        typeMapping.put("float", "Float");
        typeMapping.put("long", "Long");
        typeMapping.put("double", "Double");
        typeMapping.put("ByteArray", "ByteArray");
        typeMapping.put("list", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("object", "Any");
        typeMapping.put("binary", "Array<kotlin.Byte>");

        typeMapping.put("date", "java.time.LocalDate");
        typeMapping.put("date-time", "java.time.OffsetDateTime");
        typeMapping.put("Date", "java.time.LocalDate");
        typeMapping.put("DateTime", "java.time.OffsetDateTime");

        importMapping.put("Date", "java.time.LocalDate");
        importMapping.put("DateTime", "java.time.OffsetDateTime");

        // use resource for file handling
        typeMapping.put("file", "org.springframework.core.io.Resource");


        languageSpecificPrimitives.addAll(Arrays.asList(
                "Any",
                "Byte",
                "ByteArray",
                "Short",
                "Int",
                "Long",
                "Float",
                "Double",
                "Boolean",
                "Char",
                "String",
                "Array",
                "List",
                "Map",
                "Set"
        ));

        addOption(TITLE, "server title name or client service name", title);
        addOption(BASE_PACKAGE, "base package (invokerPackage) for generated code", basePackage);
        addOption(SERVER_PORT, "configuration the port in which the sever is to run on", serverPort);
        addOption(CodegenConstants.MODEL_PACKAGE, "model package for generated code", modelPackage);
        addOption(CodegenConstants.API_PACKAGE, "api package for generated code", apiPackage);
        addSwitch(EXCEPTION_HANDLER, "generate default global exception handlers", exceptionHandler);
        addSwitch(GRADLE_BUILD_FILE, "generate a gradle build file using the Kotlin DSL", gradleBuildFile);
        addSwitch(SWAGGER_ANNOTATIONS, "generate swagger annotations to go alongside controllers and models", swaggerAnnotations);
        addSwitch(SERVICE_INTERFACE, "generate service interfaces to go alongside controllers. In most " +
                "cases this option would be used to update an existing project, so not to override implementations. " +
                "Useful to help facilitate the generation gap pattern", serviceInterface);
        addSwitch(SERVICE_IMPLEMENTATION, "generate stub service implementations that extends service " +
                "interfaces. If this is set to true service interfaces will also be generated", serviceImplementation);
        addSwitch(USE_BEANVALIDATION, "Use BeanValidation API annotations to validate data types", useBeanValidation);

        supportedLibraries.put(SPRING_BOOT, "Spring-boot Server application.");
        setLibrary(SPRING_BOOT);

        CliOption cliOpt = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        cliOpt.setDefault(SPRING_BOOT);
        cliOpt.setEnum(supportedLibraries);
        cliOptions.add(cliOpt);
    }

    public String getResourceFolder() {
        return this.resourceFolder;
    }

    public void setResourceFolder(String resourceFolder) {
        this.resourceFolder = resourceFolder;
    }

    public String getBasePackage() {
        return this.basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getInvokerPackage() {
        return this.invokerPackage;
    }

    public void setInvokerPackage(String invokerPackage) {
        this.invokerPackage = invokerPackage;
    }

    public String getServerPort() {
        return this.serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public boolean getExceptionHandler() {
        return this.exceptionHandler;
    }

    public void setExceptionHandler(boolean exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public boolean getGradleBuildFile() {
        return this.gradleBuildFile;
    }

    public void setGradleBuildFile(boolean gradleBuildFile) {
        this.gradleBuildFile = gradleBuildFile;
    }

    public boolean getSwaggerAnnotations() {
        return this.swaggerAnnotations;
    }

    public void setSwaggerAnnotations(boolean swaggerAnnotations) {
        this.swaggerAnnotations = swaggerAnnotations;
    }

    public boolean getServiceInterface() {
        return this.serviceInterface;
    }

    public void setServiceInterface(boolean serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public boolean getServiceImplementation() {
        return this.serviceImplementation;
    }

    public void setServiceImplementation(boolean serviceImplementation) {
        this.serviceImplementation = serviceImplementation;
    }

    public boolean getUseBeanValidation() {
        return this.useBeanValidation;
    }

    @Override
    public void setUseBeanValidation(boolean useBeanValidation) {
        this.useBeanValidation = useBeanValidation;
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return "kotlin-spring";
    }

    @Override
    public String getHelp() {
        return "Generates a Kotlin Spring application.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        // optional jackson mappings for BigDecimal support
        importMapping.put("ToStringSerializer", "com.fasterxml.jackson.databind.ser.std.ToStringSerializer");
        importMapping.put("JsonSerialize", "com.fasterxml.jackson.databind.annotation.JsonSerialize");

        // Swagger import mappings
        importMapping.put("ApiModel", "io.swagger.annotations.ApiModel");
        importMapping.put("ApiModelProperty", "io.swagger.annotations.ApiModelProperty");

        // Jackson import mappings
        importMapping.put("JsonValue", "com.fasterxml.jackson.annotation.JsonValue");
        importMapping.put("JsonCreator", "com.fasterxml.jackson.annotation.JsonCreator");
        importMapping.put("JsonProperty", "com.fasterxml.jackson.annotation.JsonProperty");
        importMapping.put("JsonSubTypes", "com.fasterxml.jackson.annotation.JsonSubTypes");
        importMapping.put("JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonTypeInfo");
        // import JsonCreator if JsonProperty is imported
        // used later in recursive import in postProcessingModels
        importMapping.put("com.fasterxml.jackson.annotation.JsonProperty", "com.fasterxml.jackson.annotation.JsonCreator");

        if (!additionalProperties.containsKey(CodegenConstants.LIBRARY)) {
            additionalProperties.put(CodegenConstants.LIBRARY, library);
        }

        // Set basePackage from invokerPackage
        if (!additionalProperties.containsKey(BASE_PACKAGE)
                && additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            this.setBasePackage((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
            this.setInvokerPackage((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
            additionalProperties.put(BASE_PACKAGE, basePackage);
            LOGGER.info("Set base package to invoker package (" + basePackage + ")");
        }

        if (additionalProperties.containsKey(BASE_PACKAGE)) {
            this.setBasePackage((String) additionalProperties.get(BASE_PACKAGE));
        } else {
            additionalProperties.put(BASE_PACKAGE, basePackage);
        }

        if (additionalProperties.containsKey(SERVER_PORT)) {
            this.setServerPort((String) additionalProperties.get(SERVER_PORT));
        } else {
            additionalProperties.put(SERVER_PORT, serverPort);
        }

        if (additionalProperties.containsKey(EXCEPTION_HANDLER)) {
            this.setExceptionHandler(Boolean.valueOf(additionalProperties.get(EXCEPTION_HANDLER).toString()));
        }
        writePropertyBack(EXCEPTION_HANDLER, exceptionHandler);

        if (additionalProperties.containsKey(GRADLE_BUILD_FILE)) {
            this.setGradleBuildFile(Boolean.valueOf(additionalProperties.get(GRADLE_BUILD_FILE).toString()));
        }
        writePropertyBack(GRADLE_BUILD_FILE, gradleBuildFile);

        if (additionalProperties.containsKey(SWAGGER_ANNOTATIONS)) {
            this.setSwaggerAnnotations(Boolean.valueOf(additionalProperties.get(SWAGGER_ANNOTATIONS).toString()));
        }
        writePropertyBack(SWAGGER_ANNOTATIONS, swaggerAnnotations);

        if (additionalProperties.containsKey(SERVICE_INTERFACE)) {
            this.setServiceInterface(Boolean.valueOf(additionalProperties.get(SERVICE_INTERFACE).toString()));
        }
        writePropertyBack(SERVICE_INTERFACE, serviceInterface);

        if (additionalProperties.containsKey(SERVICE_IMPLEMENTATION)) {
            this.setServiceImplementation(Boolean.valueOf(additionalProperties.get(SERVICE_IMPLEMENTATION).toString()));
        }
        writePropertyBack(SERVICE_IMPLEMENTATION, serviceImplementation);

        if (additionalProperties.containsKey(USE_BEANVALIDATION)) {
            this.setUseBeanValidation(convertPropertyToBoolean(USE_BEANVALIDATION));
        }
        writePropertyBack(USE_BEANVALIDATION, useBeanValidation);

        modelTemplateFiles.put("model.mustache", ".kt");
        apiTemplateFiles.put("api.mustache", ".kt");
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));

        if (this.serviceInterface) {
            apiTemplateFiles.put("service.mustache", "Service.kt");
        } else if (this.serviceImplementation) {
            LOGGER.warn("If you set `serviceImplementation` to true, `serviceInterface` will also be set to true");
            additionalProperties.put(SERVICE_INTERFACE, true);
            apiTemplateFiles.put("service.mustache", "Service.kt");
            apiTemplateFiles.put("serviceImpl.mustache", "ServiceImpl.kt");
        }

        if (this.exceptionHandler) {
            supportingFiles.add(new SupportingFile("exceptions.mustache",
                    sanitizeDirectory(sourceFolder + File.separator + apiPackage), "Exceptions.kt"));
        }

        if (library.equals(SPRING_BOOT)) {
            LOGGER.info("Setup code generator for Kotlin Spring Boot");
            supportingFiles.add(new SupportingFile("pom.mustache", "", "pom.xml"));

            if (this.gradleBuildFile) {
                supportingFiles.add(new SupportingFile("buildGradleKts.mustache", "", "build.gradle.kts"));
                supportingFiles.add(new SupportingFile("settingsGradle.mustache", "", "settings.gradle"));
            }

            supportingFiles.add(new SupportingFile("application.mustache", resourceFolder, "application.yaml"));
            supportingFiles.add(new SupportingFile("springBootApplication.mustache",
                    sanitizeDirectory(sourceFolder + File.separator + basePackage), "Application.kt"));
        }

        addMustacheLambdas(additionalProperties);

        // spring uses the jackson lib, and we disallow configuration.
        additionalProperties.put("jackson", "true");
    }

    private void addMustacheLambdas(final Map<String, Object> objs) {
        Map<String, Mustache.Lambda> lambdas =
                new ImmutableMap.Builder<String, Mustache.Lambda>()
                        .put("escapeDoubleQuote", new EscapeLambda("\"", "\\\""))
                        .build();

        if (objs.containsKey(LAMBDA)) {
            LOGGER.warn("The lambda property is a reserved word, and will be overwritten!");
        }
        objs.put(LAMBDA, lambdas);
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        if (!additionalProperties.containsKey(TITLE)) {
            // The purpose of the title is for:
            // - README documentation
            // - The spring.application.name
            // - And linking the @RequestMapping
            // This is an additional step we add when pre-processing the API spec, if
            // there is no user configuration set for the `title` of the project,
            // we try build and normalise a title from the API spec itself.
            String title = openAPI.getInfo().getTitle();

            // Drop any API suffix
            if (title != null) {
                title = title.trim().replace(" ", "-");
                if (title.toUpperCase(Locale.ROOT).endsWith("API"))
                    title = title.substring(0, title.length() - 3);

                this.title = camelize(sanitizeName(title), true);
            }
            additionalProperties.put(TITLE, this.title);
        }

        if (!additionalProperties.containsKey(SERVER_PORT)) {
            URL url = URLPathUtils.getServerURL(openAPI);
            this.additionalProperties.put(SERVER_PORT, URLPathUtils.getPort(url, 8080));
        }

        // TODO: Handle tags
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        if ("null".equals(property.example)) {
            property.example = null;
        }

        //Add imports for Jackson
        if (!Boolean.TRUE.equals(model.isEnum)) {
            model.imports.add("JsonProperty");
            if (Boolean.TRUE.equals(model.hasEnums)) {
                model.imports.add("JsonValue");
            }
        } else {
            //Needed imports for Jackson's JsonCreator
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonCreator");
            }
        }

        if (model.discriminator != null && additionalProperties.containsKey("jackson")) {
            model.imports.addAll(Arrays.asList("JsonSubTypes", "JsonTypeInfo"));
        }
    }

    @Override
    public Map<String, Object> postProcessModelsEnum(Map<String, Object> objs) {
        objs = super.postProcessModelsEnum(objs);

        //Add imports for Jackson
        List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports");
        List<Object> models = (List<Object>) objs.get("models");

        models.stream()
                .map(mo -> (Map<String, Object>) mo)
                .map(mo -> (CodegenModel) mo.get("model"))
                .filter(cm -> Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null)
                .forEach(cm -> {
                    cm.imports.add(importMapping.get("JsonValue"));
                    Map<String, String> item = new HashMap<>();
                    item.put("import", importMapping.get("JsonValue"));
                    imports.add(item);
                });

        return objs;
    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            ops.forEach(operation -> {
                List<CodegenResponse> responses = operation.responses;
                if (responses != null) {
                    responses.forEach(resp -> {

                        if ("0".equals(resp.code)) {
                            resp.code = "200";
                        }

                        doDataTypeAssignment(resp.dataType, new DataTypeAssigner() {
                            @Override
                            public void setReturnType(final String returnType) {
                                resp.dataType = returnType;
                            }

                            @Override
                            public void setReturnContainer(final String returnContainer) {
                                resp.containerType = returnContainer;
                            }
                        });
                    });
                }
                doDataTypeAssignment(operation.returnType, new DataTypeAssigner() {

                    @Override
                    public void setReturnType(final String returnType) {
                        operation.returnType = returnType;
                    }

                    @Override
                    public void setReturnContainer(final String returnContainer) {
                        operation.returnContainer = returnContainer;
                    }
                });
//                if(implicitHeaders){
//                    removeHeadersFromAllParams(operation.allParams);
//                }
            });
        }

        return objs;
    }

    private interface DataTypeAssigner {
        void setReturnType(String returnType);

        void setReturnContainer(String returnContainer);
    }

    /**
     * @param returnType       The return type that needs to be converted
     * @param dataTypeAssigner An object that will assign the data to the respective fields in the model.
     */
    private void doDataTypeAssignment(final String returnType, DataTypeAssigner dataTypeAssigner) {
        if (returnType == null) {
            dataTypeAssigner.setReturnType("Unit");
        } else if (returnType.startsWith("List")) {
            int end = returnType.lastIndexOf(">");
            if (end > 0) {
                dataTypeAssigner.setReturnType(returnType.substring("List<".length(), end).trim());
                dataTypeAssigner.setReturnContainer("List");
            }
        } else if (returnType.startsWith("Map")) {
            int end = returnType.lastIndexOf(">");
            if (end > 0) {
                dataTypeAssigner.setReturnType(returnType.substring("Map<".length(), end).split(",")[1].trim());
                dataTypeAssigner.setReturnContainer("Map");
            }
        }
    }

    private static String sanitizeDirectory(String in) {
        return in.replace(".", File.separator);
    }

    // TODO could probably be made more generic, and moved to the `mustache` package if required by other components.
    private static class EscapeLambda implements Mustache.Lambda {
        private String from;
        private String to;

        EscapeLambda(final String from, final String to) {
            this.from = from;
            this.to = Matcher.quoteReplacement(to);
        }

        @Override
        public void execute(Template.Fragment fragment, Writer writer) throws IOException {
            writer.write(fragment.execute().replaceAll(from, to));
        }
    }

    // Can't figure out the logic in DefaultCodegen but optional vars are getting duplicated when there's
    // inheritance involved. Also, isInherited doesn't seem to be getting set properly ¯\_(ツ)_/¯
    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        CodegenModel m = super.fromModel(name, schema);

        m.optionalVars = m.optionalVars.stream().distinct().collect(Collectors.toList());
        m.allVars.stream().filter(p -> !m.vars.contains(p)).forEach(p -> p.isInherited = true);

        return m;
    }

    /**
     * Output the proper model name (capitalized).
     * In case the name belongs to the TypeSystem it won't be renamed.
     *
     * @param name the name of the model
     * @return capitalized model name
     */
    @Override
    public String toModelName(final String name) {
        // Allow for explicitly configured spring.*
        if (name.startsWith("org.springframework.") ) {
            return name;
        }
        return super.toModelName(name);
    }

    /**
     * Check the type to see if it needs import the library/module/package
     *
     * @param type name of the type
     * @return true if the library/module/package of the corresponding type needs to be imported
     */
    @Override
    protected boolean needToImport(String type) {
        // provides extra protection against improperly trying to import language primitives and java types
        boolean imports = !type.startsWith("org.springframework.") && super.needToImport(type);
        return imports;
    }
}
