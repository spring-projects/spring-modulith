/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.jobrunr;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collection;
import java.util.Set;

import javax.sql.DataSource;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration;
import org.jobrunr.spring.autoconfigure.JobRunrProperties;
import org.jobrunr.spring.autoconfigure.JobRunrProperties.Database;
import org.jobrunr.spring.autoconfigure.storage.JobRunrSqlStorageAutoConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.StorageProviderUtils.DatabaseOptions;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Copy of {@link JobRunrSqlStorageAutoConfiguration} to make sure the
 * {@link org.jobrunr.storage.sql.SqlStorageProvider} properly participates in Spring-managed transactions. This is
 * achieved by decorating the {@link DataSource} in a way that the returned {@link Connection}s don't act on
 * {@link Connection#setAutoCommit(boolean)} and {@link Connection#commit()} in case they are triggered from within a
 * Spring-managed transaction.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Irma - Black Sun (Acoustic)
 */
@AutoConfiguration(
		after = DataSourceAutoConfiguration.class,
		before = { JobRunrSqlStorageAutoConfiguration.class, JobRunrAutoConfiguration.class })
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "jobrunr.database", name = "type", havingValue = "sql", matchIfMissing = true)
class ModulithJobRunrSqlStorageProviderAutoConfiguration {

	@Bean(name = "storageProvider", destroyMethod = "close")
	@DependsOnDatabaseInitialization
	@ConditionalOnMissingBean
	public StorageProvider sqlStorageProvider(BeanFactory beanFactory, JobMapper jobMapper,
			JobRunrProperties properties) {

		var databaseProperties = properties.getDatabase();
		var tablePrefix = databaseProperties.getTablePrefix();
		var databaseOptions = databaseProperties.isSkipCreate() ? DatabaseOptions.SKIP_CREATE : DatabaseOptions.CREATE;
		var dataSource = getDataSource(beanFactory, databaseProperties);
		var decorated = decoratedDataSource(dataSource);
		var storageProvider = SqlStorageProviderFactory.using(decorated, tablePrefix,
				databaseOptions);

		storageProvider.setJobMapper(jobMapper);

		return storageProvider;
	}

	private static DataSource decoratedDataSource(DataSource dataSource) {
		return new CommitSuppressingDataSourceProxy(dataSource);
	}

	private static DataSource getDataSource(BeanFactory beanFactory, Database properties) {

		var datasourceReference = properties.getDatasource();

		if (StringUtils.hasText(datasourceReference)) {
			return beanFactory.getBean(datasourceReference, DataSource.class);
		} else {
			return beanFactory.getBean(DataSource.class);
		}
	}

	/**
	 * A {@link TransactionAwareDataSourceProxy} that additionally decorates the {@link Connection} so that it suppresses
	 * calls to {@link Connection#setAutoCommit(boolean)} and {@link Connection#commit()} in case a Spring-managed
	 * transaction is active.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.1
	 * @soundtrack Irma - Black Sun (Acoustic)
	 */
	private static class CommitSuppressingDataSourceProxy extends TransactionAwareDataSourceProxy {

		/**
		 * Creates a new {@link CommitSuppressingDataSourceProxy} for the given {@link DataSource}.
		 *
		 * @param targetDataSource must not be {@literal null}.
		 */
		CommitSuppressingDataSourceProxy(DataSource targetDataSource) {

			super(targetDataSource);

			Assert.notNull(targetDataSource, "DataSource must not be null!");
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy#getTransactionAwareConnectionProxy(javax.sql.DataSource)
		 */
		@Override
		protected Connection getTransactionAwareConnectionProxy(DataSource targetDataSource) {

			var connection = super.getTransactionAwareConnectionProxy(targetDataSource);

			return (Connection) Proxy.newProxyInstance(
					ConnectionProxy.class.getClassLoader(),
					new Class<?>[] { ConnectionProxy.class },
					new CommitSuppressingInvocationHandler(connection));
		}

		/**
		 * An {@link InvocationHandler} that prevents calls to {@link Connection#setAutoCommit(boolean)} and
		 * {@link Connection#commit()} on the given {@link Connection}.
		 *
		 * @author Oliver Drotbohm
		 * @since 2.1
		 * @soundtrack Irma - Black Sun (Acoustic)
		 */
		private static class CommitSuppressingInvocationHandler implements InvocationHandler {

			private static final Collection<String> SUPPRESSED_METHODS = Set.of("commit", "setAutoCommit");

			private final Connection connection;

			/**
			 * Creates a new {@link CommitSuppressingDataSource} for the given {@link Connection}.
			 *
			 * @param connection must not be {@literal null}.
			 */
			CommitSuppressingInvocationHandler(Connection connection) {

				Assert.notNull(connection, "Object must not be null!");

				this.connection = connection;
			}

			/*
			 * (non-Javadoc)
			 * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
			 */
			@Override
			public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

				if (!TransactionSynchronizationManager.isActualTransactionActive()) {
					return method.invoke(connection, args);
				}

				if (SUPPRESSED_METHODS.contains(method.getName())) {
					return null;
				}

				return method.invoke(connection, args);
			}
		}
	}
}
