sumo-logback-appender
===================

A logback appender that sends logs in json format straight to Sumo Logic.

**`HOW-TO-USE`**

1. Add the following dependency to your application:

        <dependency>
            <groupId>com.github.wmifsud.sumo</groupId>
            <artifactId>sumo-logback-appender-java-7</artifactId>
            <version>1.3.0</version>
        </dependency>
        
2. Add the following logback-spring.xml to your application:

        <?xml version="1.0" encoding="UTF-8"?>
        <configuration scan="true">
            <!-- To enable JMX Management -->
            <jmxConfigurator/>
        
            <include resource="org/springframework/boot/logging/logback/base.xml"/>
        
            <logger name="org.springframework.web" level="INFO"/>
            <logger name="org.springframework.data" level="INFO"/>
            <logger name="org.hibernate.SQL" level="INFO"/>
            <springProfile name="!local">
        
                <property scope="context" name="git_tags" value="${git.tags}"/>
                <property scope="context" name="git_branch" value="${git.branch}"/>
                <property scope="context" name="git_version_id" value="${git.version.id}"/>
        
                <springProperty scope="context" name="application_component_name" source="spring.application.name"/>
                <springProperty name="sumo_logic_url" source="sumo.logic.url"/>
        
                <appender name="sumoLogicAppender" class="com.sumologic.logback.BufferedSumoLogicAppender">
                    <url>${sumo_logic_url}</url>
                    <messagesPerRequest>1</messagesPerRequest>
                    <layout class="com.auctionedge.logback.EdgeConnectJsonLayout">
                        <includeMDC>false</includeMDC>
                        <auctionId>${get_from_environment}</auctionId>
                        <!-- auctionIdLogKey defaults to pipeline_id -->
                        <auctionIdLogKey>some_other_key</auctionIdLogKey>
                        <jsonFormatter class="ch.qos.logback.contrib.jackson.JacksonJsonFormatter">
                            <prettyPrint>false</prettyPrint>
                        </jsonFormatter>
                        <timestampFormat>yyyy-MM-dd' 'HH:mm:ss.SSS' 'Z</timestampFormat>
                        <appendLineSeparator>true</appendLineSeparator>
                    </layout>
                </appender>
        
                <root level="INFO">
                    <appender-ref ref="sumoLogicAppender"/>
                </root>
            </springProfile>
        </configuration>

3. Add the following property to your application/bootstrap.yml file where logs will be posted to:

        sumo.logic.url=${your_sumo_logic_url_collector}
