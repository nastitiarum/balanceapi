package com.assesment.balance;

import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Jedis;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.assesment.balance.data.Account;
import com.assesment.balance.repo.AccountRepo;
import com.assesment.balance.repo.UserRepo;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = BalanceApplication.class)
class BalanceApplicationTests {

	@Autowired
	MockMvc mvc;
	
	@Autowired
	AccountRepo accountRepo;
	
	@Autowired
	UserRepo userRepo;
	
	Jedis jedis;
	String hostRedis = "127.0.0.1";
	int portRedis = 6379;
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public BalanceApplicationTests() {
		redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(hostRedis, portRedis);
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DATE, 1);
		jedis.set("123testing123", sdf.format(cal.getTime()));
		jedis.close();
	}
	
	@Test
	public void login_success_thenStatus200() throws Exception{
		//Prepare data
		String user = "balanceadmin";
		String pass = "balanceadmin123";
		String auth = user + ":" + pass;
		String authHeader = Base64.encodeBase64String(auth.getBytes());
		//Do testing
		mvc.perform(MockMvcRequestBuilders.post("/login").header("Authorization", "Bearer " + authHeader))
		.andDo(print())
		.andExpect(status().isOk())
		.andExpect(jsonPath("token").isNotEmpty());
	}
	
	@Test
	public void login_failed_thenStatus400() throws Exception{
		//Prepare data
		String user = "balance";
		String pass = "balanceadmin";
		String auth = user + ":" + pass;
		String authHeader = Base64.encodeBase64String(auth.getBytes());
		//Do testing
		mvc.perform(MockMvcRequestBuilders.post("/login").header("Authorization", "Bearer " + authHeader))
		.andDo(print())
		.andExpect(status().isBadRequest())
		.andExpect(jsonPath("token").isEmpty());
	}
	
	@Test
	public void createaccount_success_thenStatus201() throws Exception{
		//Prepare data
		String authHeader = "123testing123";
		//Do testing
		String bodyReq = "{\"name\": \"123autotesting\", \"description\": \"123account for autotesting\"}";
		mvc.perform(MockMvcRequestBuilders.post("/account/create").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isCreated())
		.andExpect(jsonPath("accountnumber").isNotEmpty());
		//Clear data
		Account acc = accountRepo.findByNameAndDescription("123autotesting", "123account for autotesting");
		accountRepo.delete(acc);
		//accountRepo.deleteByNameAndDescription("123autotesting", "123account for autotesting");
	}
	
	@Test
	public void createaccount_failed_thenStatus400() throws Exception{
		//Prepare data
		String authHeader = "123testing1234";
		//Do testing
		String bodyReq = "{\"name\": \"autotesting\", \"description\": \"account for autotesting\"}";
		mvc.perform(MockMvcRequestBuilders.post("/account/create").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void topup_success_thenStatus200() throws Exception{
		//Prepare data
		String authHeader = "123testing123";
		Account account = new Account();
		account.setAccountnumber("9999999tes");
		account.setBalance(0);
		account.setCreated(sdf.format(new Date()));
		account.setDescription("account for autotesting");
		account.setName("autotesting");
		accountRepo.save(account);
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/topup").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isOk())
		.andExpect(jsonPath("balance").value("10.0"));
		//Clear data
		accountRepo.delete(account);
	}
	
	@Test
	public void topup_failed_thenStatus400() throws Exception{
		//Prepare data
		String authHeader = "123testing1234";
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/topup").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void redeem_success_thenStatus200() throws Exception{
		//Prepare data
		String authHeader = "123testing123";
		Account account = new Account();
		account.setAccountnumber("9999999tes");
		account.setBalance(20);
		account.setCreated(sdf.format(new Date()));
		account.setDescription("account for autotesting");
		account.setName("autotesting");
		accountRepo.save(account);
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/redeem").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isOk())
		.andExpect(jsonPath("balance").value("10.0"));
		//Clear data
		accountRepo.delete(account);
	}
	
	@Test
	public void redeem_failed_thenStatus400() throws Exception{
		//Prepare data
		String authHeader = "123testing1234";
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/redeem").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void redeem_balanceNotEnough_thenStatus400() throws Exception{
		//Prepare data
		String authHeader = "123testing1234";
		Account account = new Account();
		account.setAccountnumber("9999999tes");
		account.setBalance(20);
		account.setCreated(sdf.format(new Date()));
		account.setDescription("account for autotesting");
		account.setName("autotesting");
		accountRepo.save(account);
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 30}";
		mvc.perform(MockMvcRequestBuilders.post("/account/redeem").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isBadRequest());
		//Clear data
		accountRepo.delete(account);
	}
	
	@Test
	public void reverse_success_thenStatus200() throws Exception{
		//Prepare data
		String authHeader = "123testing123";
		Account account = new Account();
		account.setAccountnumber("9999999tes");
		account.setBalance(0);
		account.setCreated(sdf.format(new Date()));
		account.setDescription("account for autotesting");
		account.setName("autotesting");
		accountRepo.save(account);
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/reverse").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isOk())
		.andExpect(jsonPath("balance").value("10.0"));
		//Clear data
		accountRepo.delete(account);
	}
	
	@Test
	public void reverse_failed_thenStatus400() throws Exception{
		//Prepare data
		String authHeader = "123testing1234";
		//Do testing
		String bodyReq = "{\"accountnumber\": \"9999999tes\", \"amount\": 10}";
		mvc.perform(MockMvcRequestBuilders.post("/account/reverse").header("Authorization", "Basic " + authHeader)
				.contentType(MediaType.APPLICATION_JSON).content(bodyReq))
		.andDo(print())
		.andExpect(status().isBadRequest());
	}
}
