package com.campusops.user.repository;

import com.campusops.enums.Role;
import com.campusops.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByDepartment(String department);

    List<User> findByRoleAndDepartment(Role role, String department);
}