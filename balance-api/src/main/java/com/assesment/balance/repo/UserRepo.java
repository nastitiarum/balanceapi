package com.assesment.balance.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.assesment.balance.data.User;

public interface UserRepo extends CrudRepository<User, String> {
	@Query(value = "SELECT * FROM user u WHERE LOWER(username) = LOWER(?1) AND password = MD5(?2)", nativeQuery = true)
	User getByUserNameAndPassword(String user, String pass);
}
