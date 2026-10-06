package com.guilhermelevi.barbearia.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService adminUsers(@Value("${app.admin.username:}") String username,
                                 @Value("${app.admin.password-hash:}") String hash) {
        var users = new InMemoryUserDetailsManager();
        // Sem configuracao, o painel permanece inacessivel e o bot pode iniciar.
        if (!username.isBlank() && hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            users.createUser(User.withUsername(username).password(hash).roles("ADMIN").build());
        }
        return users;
    }

    @Bean
    org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(java.util.List.of("https://zaluratech.com.br", "https://www.zaluratech.com.br"));
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/csrf", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/webhook/whatsapp", "/api/meta/whatsapp/validar-token/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhook/whatsapp", "/api/meta/whatsapp/connect").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhook/whatsapp", "/api/meta/whatsapp/connect"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(401)))
                .formLogin(login -> login.loginPage("/api/admin/login").loginProcessingUrl("/api/admin/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, error) -> response.setStatus(401)).permitAll())
                .logout(logout -> logout.logoutUrl("/api/admin/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .build();
    }
}
