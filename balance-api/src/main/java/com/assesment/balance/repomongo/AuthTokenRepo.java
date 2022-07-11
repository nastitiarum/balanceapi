package com.assesment.balance.repomongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.assesment.balance.data.AuthToken;

public interface AuthTokenRepo extends MongoRepository<AuthToken, String> {
	public AuthToken findByToken(String token);
}
