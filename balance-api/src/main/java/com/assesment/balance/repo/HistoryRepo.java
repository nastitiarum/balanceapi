package com.assesment.balance.repo;

import org.springframework.data.repository.CrudRepository;

import com.assesment.balance.data.History;

public interface HistoryRepo extends CrudRepository<History, Integer> {

}
