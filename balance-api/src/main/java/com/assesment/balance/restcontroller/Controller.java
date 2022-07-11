package com.assesment.balance.restcontroller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.assesment.balance.data.Account;
import com.assesment.balance.data.AuthToken;
import com.assesment.balance.data.History;
import com.assesment.balance.data.Type;
import com.assesment.balance.data.User;
import com.assesment.balance.pojo.RequestBalance;
import com.assesment.balance.pojo.RequestCreateAccount;
import com.assesment.balance.pojo.ResponseBalance;
import com.assesment.balance.pojo.ResponseCreateAccount;
import com.assesment.balance.pojo.ResponseLogin;
import com.assesment.balance.repo.AccountRepo;
import com.assesment.balance.repo.HistoryRepo;
import com.assesment.balance.repo.TypeRepo;
import com.assesment.balance.repo.UserRepo;
import com.assesment.balance.repomongo.AuthTokenRepo;
import com.assesment.balance.utility.AppLogUtil;
import com.google.gson.Gson;

import redis.clients.jedis.Jedis;

@RestController
public class Controller {
	@Autowired
	UserRepo userRepo;
	@Autowired
	AccountRepo accountRepo;
	@Autowired
	HistoryRepo historyRepo;
	@Autowired
	TypeRepo typeRepo;
	@Autowired
	AuthTokenRepo tokenRepo;
	
	@Value("#{${app.expireddays}}")
	int expDays;
	
	@Value("${spring.redis.host}")
	String hostRedis;
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	HashMap<String, Type> listType;
	Gson gson =  new Gson();
	
	Jedis jedis;
	
	@PostConstruct
	public void init(){
		// Memasukan type pada database ke dalam hash map
		List<Type> listData = typeRepo.findAll();
		listType = new HashMap<>();
		for (Type type : listData) {
			listType.put(type.getType().toLowerCase(), type);
		}
		// Inisialisasi redis
		//jedis = new Jedis(hostRedis, portRedis);
		jedis = new Jedis(hostRedis);
		/* Mengambil seluruh data pada mongo DB dan memasukan ke redis jika data belum ada 
		*  dengan tujuan jika server redis down terdapat backup pada mongo DB dan client tidak perlu login ulang */
		List<AuthToken> listToken = tokenRepo.findAll();
		for(AuthToken token : listToken){
			if(jedis.get(token.getToken()) == null){
				jedis.set(token.getToken(), sdf.format(token.getExpiry()));
			}
		}
		AppLogUtil.WriteInfoLog("Aplikasi siap digunakan..");
	}

	/* 
	 * API login ini digunakan untuk mendapatkan auth token untuk akses ke API lain
	 * @request header Authorization: Bearer base64(username:password)
	 * @return 200 jika user dan password benar
	 * 		   400 jika request tidak sesuai dan user password salah 
	 * */
	@PostMapping(value = "#{${app.path.login}}")
	public ResponseEntity<ResponseLogin> login(@RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseLogin> response = null;
		ResponseLogin resp = new ResponseLogin();
		try{
			// Mengambil username dan password dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[login-request] header authorization " + auth);
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			String[] authData = auth.split(" ");
			if(!authData[0].equalsIgnoreCase("Bearer")){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Decode user password
			String userPass = new String(Base64.decodeBase64(authData[1]));
			String[] dataUserPass = userPass.split(":");
			if(dataUserPass.length < 2) {
				resp.setStatus("Invalid user password");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Get data dari database berdasarkan user password 
			User user = userRepo.getByUserNameAndPassword(dataUserPass[0], dataUserPass[1]);
			if(user == null){
				resp.setStatus("Invalid user password");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}else{
				// Generate auth token
				String token = UUID.randomUUID().toString().replace("-", "");
				// Generate expired auth token
				Calendar cal = Calendar.getInstance();
				cal.setTime(new Date());
				cal.add(Calendar.DATE, expDays);
				String exp = sdf.format(cal.getTime());
				// Menyimpan ke dalam redis
				jedis.set(token, exp);
				resp.setToken(token);
				resp.setStatus("Success");
				resp.setExpireddate(exp);
				// Menyimpan ke dalam mongoDB (untuk backup ketika redis down)
				AuthToken authToken = new AuthToken();
				authToken.setToken(token);
				cal.add(Calendar.HOUR_OF_DAY, 7);
				authToken.setExpiry(cal.getTime());
				tokenRepo.save(authToken);
				
				response = new ResponseEntity<>(resp, HttpStatus.OK);
				AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.OK + "; body:" + gson.toJson(resp));
			}
			
		} catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[login] Terdapat kesalahan", e);
			AppLogUtil.WriteInfoLog("[login-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/* 
	 * API create account ini digunakan untuk membuat account baru
	 * @request header Authorization: Basic authtoken
	 * @param name berisi nama account
	 * @param description berisi deskripsi dari account tersebut
	 * @return 200 jika sukses membuat account dengan @param accountnumber
	 * 		   400 jika auth token salah
	 */
	@PostMapping(value = {"#{${app.path.account}}"})
	public ResponseEntity<ResponseCreateAccount> createAccount(@RequestBody RequestCreateAccount req, 
			@RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseCreateAccount> response = null;
		ResponseCreateAccount resp = new ResponseCreateAccount();
		try{
			// Mengambil auth token dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[createaccount-request] header " + auth + "; body:" + gson.toJson(req));
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[createaccount-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(!checkToken(auth)){
				resp.setStatus("Invalid token");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[createaccount-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Generate account number
			String accountNum = UUID.randomUUID().toString().replace("-", "");
			Account account = new Account();
			account.setAccountnumber(accountNum);
			account.setBalance(0);
			account.setCreated(sdf.format(new Date()));
			account.setDescription(req.getDescription());
			account.setName(req.getName());
			accountRepo.save(account);
			resp.setAccountnumber(accountNum);
			resp.setStatus("Success");
			response = new ResponseEntity<>(resp, HttpStatus.CREATED);
			AppLogUtil.WriteInfoLog("[createaccount-response] " + HttpStatus.CREATED + "; body:" + gson.toJson(resp));
		}catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[createaccount] Terdapat kesalahan ", e);
			AppLogUtil.WriteInfoLog("[createaccount-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/*
	 * API check balance ini digunakan untuk mengecek balance dari sebuah account
	 * @request header Authorization: Basic authtoken
	 * @param accountnumber berisi nama account
	 * @return 200 jika account ditemukan, @param balance berisi jumlah balance dari account tersebut
	 * 		   400 jika auth token salah atau account tidak ditemukan
	 */
	@PostMapping(value = {"#{${app.path.checkbalance}}"})
	public ResponseEntity<ResponseBalance> checkBalance(@RequestBody RequestBalance req, 
			@RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseBalance> response = null;
		ResponseBalance resp = new ResponseBalance();
		try{
			// Mengambil auth token dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[checkbalance-request] header " + auth + "; body:" + gson.toJson(req));
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[checkbalance-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(!checkToken(auth)){
				resp.setStatus("Invalid token");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[checkbalance-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Mengambil data account dari database berdasarkan account number
			Account account = accountRepo.findByAccountnumber(req.getAccountnumber());
			if(account == null){
				resp.setStatus("Invalid account number");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[checkbalance-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Menyimpan history transaksi ke dalam database
			History his = new History();
			his.setAccountnumber(req.getAccountnumber());
			his.setAmount(req.getAmount());
			his.setBalance(account.getBalance());
			his.setCreated(sdf.format(new Date()));
			his.setType(listType.get("checkbalance"));
			historyRepo.save(his);
			resp.setBalance(account.getBalance());
			resp.setStatus("Success");
			response = new ResponseEntity<>(resp, HttpStatus.CREATED);
			AppLogUtil.WriteInfoLog("[checkbalance-response] " + HttpStatus.CREATED + "; body:" + gson.toJson(resp));
		}catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[checkbalance-response]", e);
			AppLogUtil.WriteInfoLog("[checkbalance-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/*
	 * API topup ini digunakan untuk menambahkan balance dari sebuah account
	 * @request header Authorization: Basic authtoken
	 * @param accountnumber berisi nama account
	 * @param amount berisi jumlah balance yang akan ditambahkan
	 * @return 200 jika account ditemukan, @param balance berisi jumlah balance dari account tersebut
	 * 		   400 jika auth token salah atau account tidak ditemukan
	 */
	@PostMapping(value = {"#{${app.path.topup}}"})
	public ResponseEntity<ResponseBalance> topup(@RequestBody RequestBalance req, @RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseBalance> response = null;
		ResponseBalance resp = new ResponseBalance();
		try{
			// Mengambil auth token dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[topup-request] header " + auth + "; body:" + gson.toJson(req));
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[topup-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(!checkToken(auth)){
				resp.setStatus("Invalid token");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[topup-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Mengambil data account berdasarkan account number
			Account account = accountRepo.findByAccountnumber(req.getAccountnumber());
			if(account == null){
				resp.setStatus("Invalid account number");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[topup-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Menambahkan balance
			double balance = account.getBalance() + req.getAmount();
			account.setBalance(balance);
			accountRepo.save(account);
			History his = new History();
			his.setAccountnumber(req.getAccountnumber());
			his.setAmount(req.getAmount());
			his.setBalance(balance);
			his.setCreated(sdf.format(new Date()));
			his.setType(listType.get("topup"));
			historyRepo.save(his);
			resp.setBalance(balance);
			resp.setStatus("Success");
			response = new ResponseEntity<>(resp, HttpStatus.OK);
			AppLogUtil.WriteInfoLog("[topup-response] " + HttpStatus.OK + "; body:" + gson.toJson(resp));
		}catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[topup] Terdapat kesalahan", e);
			AppLogUtil.WriteInfoLog("[topup-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/*
	 * API redeem ini digunakan untuk mengurangi balance dari sebuah account
	 * @request header Authorization: Basic authtoken
	 * @param accountnumber berisi nama account
	 * @param amount berusu jumlah balance yang akan dikurangi
	 * @return 200 jika account ditemukan, @param balance berisi jumlah balance dari account tersebut
	 * 		   400 jika auth token salah atau account tidak ditemukan atau balance tidak cukup
	 * */
	@RequestMapping(value = {"#{${app.path.redeem}}"}, method = {RequestMethod.POST})
	public ResponseEntity<ResponseBalance> redeem(@RequestBody RequestBalance req, @RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseBalance> response = null;
		ResponseBalance resp = new ResponseBalance();
		try{
			// Mengambil auth token dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[redeem-request] header " + auth + "; body:" + gson.toJson(req));
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(!checkToken(auth)){
				resp.setStatus("Invalid token");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Mengambil data account berdasarkan account number
			Account account = accountRepo.findByAccountnumber(req.getAccountnumber());
			if(account == null){
				resp.setStatus("Invalid account number");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(account.getBalance() - req.getAmount() < 0){
				resp.setStatus("Balance not enough");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Mengurangi balance
			double balance = account.getBalance() - req.getAmount();
			account.setBalance(balance);
			accountRepo.save(account);
			History his = new History();
			his.setAccountnumber(req.getAccountnumber());
			his.setAmount(req.getAmount());
			his.setBalance(account.getBalance());
			his.setCreated(sdf.format(new Date()));
			his.setType(listType.get("redeem"));
			historyRepo.save(his);
			resp.setBalance(balance);
			resp.setStatus("Success");
			response = new ResponseEntity<>(resp, HttpStatus.OK);
			AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.OK + "; body:" + gson.toJson(resp));
		}catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[redeem] Terdapat kesalahan ", e);
			AppLogUtil.WriteInfoLog("[redeem-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/*
	 * API reverse ini digunakan untuk mengembalikan balance dari sebuah account jika ada transaksi yang gagal
	 * @request header Authorization: Basic authtoken
	 * @param accountnumber berisi nama account
	 * @param amount berisi jumlah balance yang akan dikembalikan
	 * @return 200 jika account ditemukan, @param balance berisi jumlah balance dari account tersebut
	 * 		   400 jika auth token salah atau account tidak ditemukan
	 */
	@PostMapping(value = {"#{${app.path.reverse}}"})
	public ResponseEntity<ResponseBalance> reverse(@RequestBody RequestBalance req, @RequestHeader HttpHeaders headers){
		ResponseEntity<ResponseBalance> response = null;
		ResponseBalance resp = new ResponseBalance();
		try{
			// Mengambil auth token dari header
			String auth = headers.get("authorization").get(0);
			AppLogUtil.WriteInfoLog("[reverse-request] header " + auth + "; body:" + gson.toJson(resp));
			if(auth == null){
				resp.setStatus("Invalid request");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[reverse-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			if(!checkToken(auth)){
				resp.setStatus("Invalid token");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[reverse-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Mengambil data account berdasarkan account number
			Account account = accountRepo.findByAccountnumber(req.getAccountnumber());
			if(account == null){
				resp.setStatus("Invalid account number");
				response = new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
				AppLogUtil.WriteInfoLog("[reverse-response] " + HttpStatus.BAD_REQUEST + "; body:" + gson.toJson(resp));
				return response;
			}
			// Menambahkan balance
			double balance = account.getBalance() + req.getAmount();
			account.setBalance(balance);
			accountRepo.save(account);
			History his = new History();
			his.setAccountnumber(req.getAccountnumber());
			his.setAmount(req.getAmount());
			his.setBalance(account.getBalance());
			his.setCreated(sdf.format(new Date()));
			his.setType(listType.get("reverse"));
			historyRepo.save(his);
			resp.setBalance(balance);
			resp.setStatus("Success");
			response = new ResponseEntity<>(resp, HttpStatus.OK);
			AppLogUtil.WriteInfoLog("[reverse-response] " + HttpStatus.OK + "; body:" + gson.toJson(resp));
		}catch(Exception e){
			resp.setStatus("Internal server error");
			response = new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
			AppLogUtil.WriteErrorLog("[reverse] Terdapat kesalahan ", e);
			AppLogUtil.WriteInfoLog("[reverse-response] " + HttpStatus.INTERNAL_SERVER_ERROR + "; body:" + gson.toJson(resp));
		}
		return response;
	}
	
	/*
	 * Fungsi ini digunakan untuk mengecek auth token valid atau tidak
	 * @return true jika auth token valid
	 * 		   false jika auth token salah atau tidak valid
	 * */
	private boolean checkToken(String header){
		String[] authData = header.split(" ");
		if(!authData[0].equalsIgnoreCase("Basic")){
			return false;
		}
		// Mengambil auth token dari redis
		if(jedis.get(authData[1]) == null){
			return false;
		}else{
			try{
				// Compare data expired pada redis dengan date now 
				if(sdf.parse(jedis.get(authData[1])).compareTo(new Date()) < 0){
					// Delete cache pada redis
					jedis.del(authData[1]);
					return false;
				}
			}catch(Exception e){
				return false;
			}
		}
		return true;
	}
}
