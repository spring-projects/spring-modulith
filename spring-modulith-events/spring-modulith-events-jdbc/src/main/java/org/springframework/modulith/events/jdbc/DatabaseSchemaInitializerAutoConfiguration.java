package org.springframework.modulith.events.jdbc;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.modulith.events.jdbc.DatabaseSchemaInitializerAutoConfiguration.ModulithEmbeddedDataSourceCondition;
import org.springframework.util.StringUtils;

/**
 * Initializes the DB schema used to store events
 *
 * @author Nikola Kološnjaji
 *
 */
@Conditional(ModulithEmbeddedDataSourceCondition.class)
@AutoConfiguration(before = JdbcEventPublicationAutoConfiguration.class)
public class DatabaseSchemaInitializerAutoConfiguration {

  private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";

  private static final String[] EMBEDDED_DATASOURCE_PREFIXES = {"jdbc:h2", "jdbc:hsqldb", "jdbc:derby"};

  @Bean
  DatabaseSchemaInitializer databaseSchemaInitializer(JdbcTemplate jdbcTemplate, ResourceLoader resourceLoader,
      DatabaseType databaseType) {
    return new DatabaseSchemaInitializer(jdbcTemplate, resourceLoader, databaseType);
  }

  static class ModulithEmbeddedDataSourceCondition extends SpringBootCondition {

    public static final String EMBEDDED_DATABASE = "embedded database";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
      ConditionMessage.Builder message = ConditionMessage.forCondition("EmbeddedDataSourceCondition");
      Environment environment = context.getEnvironment();
      if (environment.containsProperty("spring.modulith.events.jdbc-schema-initialization.enabled")) {
        boolean explicitEnable = environment.getProperty("spring.modulith.events.jdbc-schema-initialization.enabled", Boolean.class);
        if (Boolean.TRUE.equals(explicitEnable)) {
           return ConditionOutcome.match(message.found("found explicit initializer").items(""));
        }
        else{
          return ConditionOutcome.noMatch(message.didNotFind(EMBEDDED_DATABASE).atAll());
        }
      }

      if (environment.containsProperty(DATASOURCE_URL_PROPERTY)) {
        try {
          String datasourceUrl = environment.getProperty(DATASOURCE_URL_PROPERTY);
          if (StringUtils.hasText(datasourceUrl) && Arrays.stream(EMBEDDED_DATASOURCE_PREFIXES)
              .anyMatch(datasourceUrl::startsWith)) {
            return this.getEmbeddedDatabaseType(context.getClassLoader(), message);
          } else {
            return ConditionOutcome.noMatch(message.didNotFind(EMBEDDED_DATABASE).atAll());
          }
        } catch (IllegalArgumentException ex) {
          return ConditionOutcome.noMatch(message.didNotFind(EMBEDDED_DATABASE).atAll());
        }
      } else {
        return this.getEmbeddedDatabaseType(context.getClassLoader(), message);
      }
    }

    private ConditionOutcome getEmbeddedDatabaseType(ClassLoader classLoader, ConditionMessage.Builder builder) {
      EmbeddedDatabaseType type = EmbeddedDatabaseConnection.get(classLoader).getType();
      if (type == null) {
        return ConditionOutcome.noMatch(builder.didNotFind(EMBEDDED_DATABASE).atAll());
      }
      return ConditionOutcome.match(builder.found(EMBEDDED_DATABASE).items(type));
    }
  }

}
