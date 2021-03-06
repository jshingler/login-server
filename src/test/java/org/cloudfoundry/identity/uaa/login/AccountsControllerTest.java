/*******************************************************************************
 *     Cloud Foundry 
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.error.UaaException;
import org.cloudfoundry.identity.uaa.login.test.ThymeleafConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = AccountsControllerTest.ContextConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AccountsControllerTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    AccountCreationService accountCreationService;

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .build();
    }

    @Test
    public void testNewAccountPage() throws Exception {
        mockMvc.perform(get("/create_account").param("client_id", "app"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("client_id", "app"))
                .andExpect(view().name("accounts/new_activation_email"));
    }

    @Test
    public void testSendActivationEmail() throws Exception {
        MockHttpServletRequestBuilder post = post("/create_account.do")
            .param("email", "user1@example.com")
            .param("password", "password")
            .param("password_confirmation", "password")
            .param("client_id", "app");

        mockMvc.perform(post)
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("accounts/email_sent"));

        Mockito.verify(accountCreationService).beginActivation("user1@example.com", "password", "app");
    }

    @Test
    public void testSendActivationEmailWithUserNameConflict() throws Exception {
        doThrow(new UaaException("username already exists", 409)).when(accountCreationService).beginActivation("user1@example.com", "password", "app");

        MockHttpServletRequestBuilder post = post("/create_account.do")
            .param("email", "user1@example.com")
            .param("password", "password")
            .param("password_confirmation", "password")
            .param("client_id", "app");

        mockMvc.perform(post)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(view().name("accounts/new_activation_email"))
            .andExpect(model().attribute("error_message_code", "username_exists"));

        Mockito.verify(accountCreationService).beginActivation("user1@example.com", "password", "app");
    }

    @Test
    public void testInvalidEmail() throws Exception {
        MockHttpServletRequestBuilder post = post("/create_account.do")
            .param("email", "wrong")
            .param("password", "password")
            .param("password_confirmation", "password")
            .param("client_id", "app");

        mockMvc.perform(post)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(view().name("accounts/new_activation_email"))
            .andExpect(model().attribute("error_message_code", "invalid_email"));
    }

    @Test
    public void testPasswordMismatch() throws Exception {
        MockHttpServletRequestBuilder post = post("/create_account.do")
            .param("email", "user1@example.com")
            .param("password", "pass")
            .param("password_confirmation", "word")
            .param("client_id", "app");

        mockMvc.perform(post)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(view().name("accounts/new_activation_email"))
            .andExpect(model().attribute("error_message_code", "form_error"));
    }


    @Test
    public void testVerifyUser() throws Exception {
        Mockito.when(accountCreationService.completeActivation("the_secret_code"))
            .thenReturn(new AccountCreationService.AccountCreationResponse("newly-created-user-id", "username", "user@example.com", "//example.com/callback"));

        MockHttpServletRequestBuilder get = get("/verify_user")
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("//example.com/callback"));

        UaaPrincipal principal = ((UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("newly-created-user-id", principal.getId());
        assertEquals("username", principal.getName());
        assertEquals("user@example.com", principal.getEmail());
    }

    @Test
    public void testCreateAccountWithNoRedirectUrl() throws Exception {
        Mockito.when(accountCreationService.completeActivation("the_secret_code"))
            .thenReturn(new AccountCreationService.AccountCreationResponse("newly-created-user-id", "username", "user@example.com", null));

        MockHttpServletRequestBuilder get = get("/verify_user")
            .param("code", "the_secret_code");

        mockMvc.perform(get)
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("home"));

        UaaPrincipal principal = ((UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("newly-created-user-id", principal.getId());
        assertEquals("username", principal.getName());
        assertEquals("user@example.com", principal.getEmail());
    }
//
//
//    @Test
//    public void testCreateAccountWithExpiredActivationCode() throws Exception {
//        Mockito.when(accountCreationService.completeActivation("expired_code", "secret"))
//                .thenThrow(new HttpClientErrorException(BAD_REQUEST));
//
//        MockHttpServletRequestBuilder post = post("/accounts")
//                .param("email", "user@example.com")
//                .param("code", "expired_code")
//                .param("password", "secret")
//                .param("password_confirmation", "secret");
//
//        mockMvc.perform(post)
//                .andExpect(status().isUnprocessableEntity())
//                .andExpect(model().attribute("message_code", "code_expired"))
//                .andExpect(view().name("accounts/new"))
//                .andExpect(xpath("//*[@class='error-message']").string("Your activation code has expired. Please request another."));
//    }


    @Configuration
    @EnableWebMvc
    @Import(ThymeleafConfig.class)
    static class ContextConfiguration extends WebMvcConfigurerAdapter {

        @Override
        public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
            configurer.enable();
        }

        @Bean
        BuildInfo buildInfo() {
            return new BuildInfo();
        }

        @Bean
        public ResourceBundleMessageSource messageSource() {
            ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();
            resourceBundleMessageSource.setBasename("messages");
            return resourceBundleMessageSource;
        }

        @Bean
        AccountCreationService accountCreationService() {
            return Mockito.mock(AccountCreationService.class);
        }

        @Bean
        AccountsController accountsController(AccountCreationService accountCreationService) {
            return new AccountsController(accountCreationService);
        }
    }
}
