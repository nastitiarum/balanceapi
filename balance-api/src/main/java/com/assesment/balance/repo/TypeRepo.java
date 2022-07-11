package com.assesment.balance.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.assesment.balance.data.Type;

public interface TypeRepo extends JpaRepository<Type, Integer> {
	
}
