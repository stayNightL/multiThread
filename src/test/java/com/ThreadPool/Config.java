package com.ThreadPool;
import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.util.PropertiesUtil;

/**
 * This Configuration is the same as the DefaultConfiguration but shows how a custom configuration can be built
 * programmatically
 */
public class Config extends AbstractConfiguration {

    private static final long serialVersionUID = 1L;

    /**
     * The name of the default configuration.
     */
    public static final String CONFIG_NAME = "ThreadPool";

    /**
     * The System Property used to specify the logging level.
     */
    public static final String DEFAULT_LEVEL = "org.apache.logging.log4j.level";
    /**
     * The default Pattern used for the default Layout.
     */
    public static final String DEFAULT_PATTERN = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    public Config(final LoggerContext loggerContext) {
        this(loggerContext, ConfigurationSource.NULL_SOURCE);
    }

    /**
     * Constructor to create the default configuration.
     */
    public Config(final LoggerContext loggerContext, final ConfigurationSource source) {
        super(loggerContext, source);

        setName(CONFIG_NAME);
        final Layout<? extends Serializable> layout = PatternLayout.newBuilder().withPattern(DEFAULT_PATTERN)
                .withConfiguration(this)
                .build();
        final Appender appender = ConsoleAppender.createDefaultAppenderForLayout(layout);
        appender.start();
        addAppender(appender);
        final LoggerConfig root = getRootLogger();
        root.addAppender(appender, null, null);

        final String levelName = PropertiesUtil.getProperties().getStringProperty(DEFAULT_LEVEL);
        final Level level = levelName != null && Level.valueOf(levelName) != null ?
                Level.valueOf(levelName) : Level.ERROR;
        root.setLevel(level);
    }

    @Override
    protected void doConfigure() {
    }
}
