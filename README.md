# Balance API

Aplikasi ini berisi API login, create account, check balance, topup, redeem dan reverse. Teknologi yang digunakan:

 - Java 8
 - MySQL
 - Redis
 - MongoDB

Untuk binary file, karena file terlalu besar mohon untuk mengunduh pada URL berikut: [https://www.dropbox.com/s/e39eofjf1km0uzc/bin.zip?dl=0](https://www.dropbox.com/s/e39eofjf1km0uzc/bin.zip?dl=0)

Setelah unduhan selesai, extract file tersebut.

Folder bin berisi executable jar yang dapat dijalankan menggunakan docker compose, dengan perintah:
1. Untuk menjalankan aplikasi: `docker-compose up -d`
2. Untuk mematikan aplikasi beserta menghapus volume: `docker-compose down -v`
3. Untuk melihat log aplikasi: `docker logs apps_balanceapi`

Folder balance-api berisi source code dari aplikasi Balance API.

# API Login (POST)

API ini digunakan untuk mendapatkan auth token.
URL: `http://localhost:8080/login`
Request: 

    header:
    Authorization: Bearer base64(user:pass)
User default: balanceadmin
Password default: balanceadmin
Response:
HTTP 200 jika sukses
HTTP 400 jika request salah

    {
	    "status":  "Success",
	    "token":  "b07009e3b9e74de6a0814525810c4e74",
	    "expireddate":  "2022-07-12 21:23:42"
    }

## API Create Account (POST)

API ini digunakan untuk membuat account baru.
URL: `http://localhost:8080/account/create`
Request: 

    header:
    Authorization: Basic {auth_token}
    body:
    {"name": "testing", "description": "account for testing"}
    
Response: 
HTTP 201 jika sukses
HTTP 400 jika request salah

    {
	    "status":  "Success",
	    "accountnumber":  "1d78f603258341d896433336e33e9495"
    }

## API Check Balance (POST)

API ini digunakan untuk mengecek sisa balance dari sebuah account.
URL: `http://localhost:8080/account/balance`
Request: 

    header:
    Authorization: Basic {auth_token}
    body:
    {"accountnumber":  "1d78f603258341d896433336e33e9495"}
    
Response:
HTTP 200 jika sukses
HTTP 400 jika request salah atau account number tidak ditemukan

    {
	    "status":  "Success",
	    "balance":  0.0
    }



## API Topup (POST)

API ini digunakan untuk menambahkan balance dari sebuah account.
URL: `http://localhost:8080/account/topup`
Request: 

    header:
    Authorization: Basic {auth_token}
    body:
    {
	    "accountnumber":  "1d78f603258341d896433336e33e9495",
	    "amount":  10
    }
    
Response:
HTTP 200 jika sukses
HTTP 400 jika request salah atau account number tidak ditemukan

    {
	    "status":  "Success",
	    "balance":  0.0
    }
## API Redeem (POST)

API ini digunakan untuk mengurangi balance dari sebuah account.
URL: `http://localhost:8080/account/redeem`
Request: 

    header:
    Authorization: Basic {auth_token}
    body:
    {
	    "accountnumber":  "1d78f603258341d896433336e33e9495",
	    "amount":  10
    }
    
Response:
HTTP 200 jika sukses
HTTP 400 jika request salah atau account number tidak ditemukan atau balance tidak mencukupi

    {
	    "status":  "Success",
	    "balance":  0.0
    }

## API Reverse (POST)

API ini digunakan untuk menambahkan balance dari sebuah account ketika terdapat transaksi yang gagal.
URL: `http://localhost:8080/account/reverse`
Request: 

    header:
    Authorization: Basic {auth_token}
    body:
    {
	    "accountnumber":  "1d78f603258341d896433336e33e9495",
	    "amount":  10
    }
    
Response:
HTTP 200 jika sukses
HTTP 400 jika request salah atau account number tidak ditemukan

    {
	    "status":  "Success",
	    "balance":  10.0
    }
