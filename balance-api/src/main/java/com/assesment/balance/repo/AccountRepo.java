package com.assesment.balance.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.assesment.balance.data.Account;

public interface AccountRepo extends CrudRepository<Account, String> {
	Account findByAccountnumber(String accountNumber);
	@Query(value = "SELECT * FROM account u WHERE `name` = ?1 AND description = ?2 ORDER BY created DESC LIMIT 1 ", nativeQuery = true)
	Account findByNameAndDescription(String name, String description);
}
