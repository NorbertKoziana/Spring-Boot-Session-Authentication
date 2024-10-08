package com.norbertkoziana.Session.Authentication.auth;
import com.norbertkoziana.Session.Authentication.confirmation.Confirmation;
import com.norbertkoziana.Session.Authentication.confirmation.ConfirmationRepository;
import com.norbertkoziana.Session.Authentication.confirmation.ConfirmationService;
import com.norbertkoziana.Session.Authentication.model.LoginRequest;
import com.norbertkoziana.Session.Authentication.model.RegisterRequest;
import com.norbertkoziana.Session.Authentication.email.ConfirmationEmailService;
import com.norbertkoziana.Session.Authentication.token.ConfirmationTokenGenerator;
import com.norbertkoziana.Session.Authentication.user.UserRepository;
import com.norbertkoziana.Session.Authentication.user.Role;
import com.norbertkoziana.Session.Authentication.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final ConfirmationService confirmationService;

    private final ConfirmationRepository confirmationRepository;

    private final ConfirmationEmailService confirmationEmailService;

    private final ConfirmationTokenGenerator confirmationTokenGenerator;

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Override
    public void login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        //authenticate
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.getEmail(), loginRequest.getPassword());
        Authentication authentication = authenticationManager.authenticate(token);

        //session management
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @Override
    @Transactional
    public User register(RegisterRequest registerRequest) {

        User user = User.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .locked(false)
                .enabled(false)
                .role(Role.User)
                .build();

        userRepository.save(user);

        String token = createAndSaveConfirmation(user);

        confirmationEmailService.sendConfirmationMail(user.getEmail(), token);

        return user;
    }

    @Override
    @Transactional
    public void resendConfirmationMail(User user) {

        String token = confirmationRepository.findFirstByUserAndConfirmedFalseOrderByExpiresAtDesc(user)
                .filter(confirmationService::checkIfConfirmationExpiryTimeIsAtLeast5Minutes)
                .map(Confirmation::getToken)
                .orElseGet( () -> {
                        return createAndSaveConfirmation(user);
                    }
                );

        confirmationEmailService.sendConfirmationMail(user.getEmail(), token);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    String createAndSaveConfirmation(User user){
        String token = confirmationTokenGenerator.getConfirmationToken();
        Confirmation confirmation = Confirmation.builder()
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .confirmed(false)
                .user(user)
                .build();

        confirmationRepository.save(confirmation);

        return token;
    }
}
