package com.guilhermelevi.barbearia.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
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
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/admin.css", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/webhook/whatsapp", "/api/meta/whatsapp/validar-token/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhook/whatsapp", "/api/meta/whatsapp/connect").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhook/whatsapp", "/api/meta/whatsapp/connect"))
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/admin", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .build();
    }
}
