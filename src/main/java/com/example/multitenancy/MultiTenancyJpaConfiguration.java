/**
 * Copyright 2018 onwards - Sunit Katkar (sunitkatkar@gmail.com)
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

package com.example.multitenancy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.hibernate.MultiTenancyStrategy;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.example.model.Employee;
import com.example.multitenancy.MultitenancyProperties.DataSourceProperties;

/**
 * This class defines the data sources to be used for accessing the different
 * databases (one database per tenant). It generates the Hibernate session and
 * entity bean for database access via Spring JPA as well as the Transaction
 * manager to be used.
 * 
 * @author Sunit Katkar
 * @version 1.0
 * @since 1.0 (April 2018)
 */
@Configuration
@EnableConfigurationProperties({ MultitenancyProperties.class, JpaProperties.class })
@EnableTransactionManagement
public class MultiTenancyJpaConfiguration {

    @Autowired
    private JpaProperties jpaProperties;

    @Autowired
    private MultitenancyProperties multitenancyProperties;

    /**
     * Builds a map of all data sources defined in the application.yml file
     * 
     * @return
     */
    @Primary
    @Bean(name = "dataSourcesMtApp")
    public Map<String, DataSource> dataSourcesMtApp() {
        Map<String, DataSource> result = new HashMap<>();
        for (DataSourceProperties dsProperties : this.multitenancyProperties.getDataSources()) {

            DataSourceBuilder factory = DataSourceBuilder.create().url(dsProperties.getUrl())
                    .username(dsProperties.getUsername()).password(dsProperties.getPassword())
                    .driverClassName(dsProperties.getDriverClassName());

            result.put(dsProperties.getTenantId(), factory.build());
        }
        return result;
    }

    /**
     * Autowires the data sources so that they can be used by the Spring JPA to
     * access the database
     * 
     * @return
     */
    @Bean
    public MultiTenantConnectionProvider multiTenantConnectionProvider() {
        // Autowires dataSourcesMtApp
        return new DataSourceBasedMultiTenantConnectionProviderImpl();
    }

    /**
     * Since this is a multi-tenant application, Hibernate requires that the current
     * tenant identifier is resolved for use with
     * {@link org.hibernate.context.spi.CurrentSessionContext} and
     * {@link org.hibernate.SessionFactory#getCurrentSession()}
     * 
     * @return
     */
    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolverImpl();
    }

    /**
     * org.springframework.beans.factory.FactoryBean that creates a JPA
     * {@link javax.persistence.EntityManagerFactory} according to JPA's standard
     * container bootstrap contract. This is the most powerful way to set up a
     * shared JPA EntityManagerFactory in a Spring application context; the
     * EntityManagerFactory can then be passed to JPA-based DAOs via dependency
     * injection. Note that switching to a JNDI lookup or to a
     * {@link org.springframework.orm.jpa.LocalEntityManagerFactoryBean} definition
     * is just a matter of configuration!
     * 
     * @param multiTenantConnectionProvider
     * @param currentTenantIdentifierResolver
     * @return
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(
            MultiTenantConnectionProvider multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {

        Map<String, Object> hibernateProps = new LinkedHashMap<>();
        hibernateProps.putAll(this.jpaProperties.getProperties());
        hibernateProps.put(Environment.MULTI_TENANT, MultiTenancyStrategy.DATABASE);
        hibernateProps.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        hibernateProps.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);

        // No dataSource is set to resulting entityManagerFactoryBean
        LocalContainerEntityManagerFactoryBean result = new LocalContainerEntityManagerFactoryBean();
        result.setPackagesToScan(new String[] { Employee.class.getPackage().getName() });
        result.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        result.setJpaPropertyMap(hibernateProps);

        return result;
    }

    /**
     * Interface used to interact with the entity manager factory for the
     * persistence unit.
     * 
     * @param entityManagerFactoryBean
     * @return
     */
    @Bean
    public EntityManagerFactory entityManagerFactory(LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
        return entityManagerFactoryBean.getObject();
    }

    /**
     * Creates a new
     * {@link org.springframework.orm.jpa.JpaTransactionManager#JpaTransactionManager(EntityManagerFactory emf)}
     * instance.
     * 
     * {@link org.springframework.transaction.PlatformTransactionManager} is the
     * central interface in Spring's transaction infrastructure. Applications can
     * use this directly, but it is not primarily meant as API: Typically,
     * applications will work with either TransactionTemplate or declarative
     * transaction demarcation through AOP.
     * 
     * @param entityManagerFactory
     * @return
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}