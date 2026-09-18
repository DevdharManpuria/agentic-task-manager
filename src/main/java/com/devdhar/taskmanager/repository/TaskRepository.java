package com.devdhar.taskmanager.repository;

import com.devdhar.taskmanager.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    // Used by the "list tasks" tool and the paginated GET endpoint.
    // JpaSpecificationExecutor lets us build dynamic filters (status/priority) at query time
    // instead of writing a method for every filter combination.
    Page<Task> findByUserId(Long userId, Pageable pageable);
}
