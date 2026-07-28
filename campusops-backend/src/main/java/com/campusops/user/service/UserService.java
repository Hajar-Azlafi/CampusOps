package com.campusops.user.service;

import com.campusops.enums.Role;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.user.dto.PasswordResetResponseDto;
import com.campusops.user.dto.UserRequestDto;
import com.campusops.user.dto.UserResponseDto;
import com.campusops.user.entity.User;
import com.campusops.user.mapper.UserMapper;
import com.campusops.user.repository.UserRepository;
import com.campusops.user.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    public UserResponseDto createUser(UserRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Un utilisateur avec l'email " + request.getEmail() + " existe deja");
        }

        User user = userMapper.toEntity(request);
        String temporaryPassword = passwordGenerator.generate();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setActive(true);

        User savedUser = userRepository.save(user);
        return userMapper.toResponseDto(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = findUserOrThrow(id);
        return userMapper.toResponseDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> searchUsers(String keyword) {
        return userRepository.searchByKeyword(keyword).stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> filterUsers(Role role, String department) {
        List<User> users;
        if (role != null && department != null) {
            users = userRepository.findByRoleAndDepartment(role, department);
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else if (department != null) {
            users = userRepository.findByDepartment(department);
        } else {
            users = userRepository.findAll();
        }
        return users.stream().map(userMapper::toResponseDto).toList();
    }

    public UserResponseDto updateUser(Long id, UserRequestDto request) {
        User user = findUserOrThrow(id);

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Un utilisateur avec l'email " + request.getEmail() + " existe deja");
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setDepartment(request.getDepartment());
        user.setPhoneNumber(request.getPhoneNumber());

        User updatedUser = userRepository.save(user);
        return userMapper.toResponseDto(updatedUser);
    }

    public UserResponseDto changeRole(Long id, Role newRole) {
        User user = findUserOrThrow(id);
        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        return userMapper.toResponseDto(updatedUser);
    }

    public void deactivateUser(Long id) {
        User user = findUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
    }

    public void reactivateUser(Long id) {
        User user = findUserOrThrow(id);
        user.setActive(true);
        userRepository.save(user);
    }

    public PasswordResetResponseDto resetPassword(Long id) {
        User user = findUserOrThrow(id);
        String newTemporaryPassword = passwordGenerator.generate();
        user.setPassword(passwordEncoder.encode(newTemporaryPassword));
        userRepository.save(user);

        return PasswordResetResponseDto.builder()
                .email(user.getEmail())
                .temporaryPassword(newTemporaryPassword)
                .message("Mot de passe reinitialise avec succes")
                .build();
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable avec l'id " + id));
    }
}