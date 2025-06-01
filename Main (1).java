

package com.example.mobilebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class MobileBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobileBankingApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ----------------------- Entity: User -----------------------
    @Entity
    class User {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String username;
        private String password;
        private String email;
        private String mobile;
        private Double balance;

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }

        public Double getBalance() { return balance; }
        public void setBalance(Double balance) { this.balance = balance; }
    }

    // -------------------- Entity: Transaction -------------------
    @Entity
    class Transaction {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String sender;
        private String receiver;
        private Double amount;
        private String type; // sent/received/deposit/withdraw
        private LocalDateTime timestamp;

        public Long getId() { return id; }
        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }

        public String getReceiver() { return receiver; }
        public void setReceiver(String receiver) { this.receiver = receiver; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    // ----------------------- Repositories -----------------------
    @Repository
    interface UserRepository extends JpaRepository<User, Long> {
        User findByUsername(String username);
    }

    @Repository
    interface TransactionRepository extends JpaRepository<Transaction, Long> {
        List<Transaction> findBySenderOrReceiver(String sender, String receiver);
    }

    @Autowired private UserRepository userRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private BCryptPasswordEncoder encoder;

    // ------------------------ Endpoints -------------------------

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepo.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("User already exists!");
        }
        user.setPassword(encoder.encode(user.getPassword()));
        user.setBalance(1000.0);
        userRepo.save(user);
        return ResponseEntity.ok("Registration successful");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        User user = userRepo.findByUsername(username);
        if (user != null && encoder.matches(password, user.getPassword())) {
            return ResponseEntity.ok("Login successful");
        }
        return ResponseEntity.badRequest().body("Invalid credentials");
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance(@RequestParam String username) {
        User user = userRepo.findByUsername(username);
        return user != null ? ResponseEntity.ok(user.getBalance())
                            : ResponseEntity.badRequest().body("User not found");
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestParam String sender,
                                           @RequestParam String receiver,
                                           @RequestParam Double amount) {
        User from = userRepo.findByUsername(sender);
        User to = userRepo.findByUsername(receiver);
        if (from == null || to == null) return ResponseEntity.badRequest().body("User not found");
        if (from.getBalance() < amount) return ResponseEntity.badRequest().body("Insufficient balance");

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        userRepo.save(from);
        userRepo.save(to);

        Transaction t = new Transaction();
        t.setSender(sender);
        t.setReceiver(receiver);
        t.setAmount(amount);
        t.setType("transfer");
        t.setTimestamp(LocalDateTime.now());
        txRepo.save(t);

        return ResponseEntity.ok("Transfer successful");
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(@RequestParam String username, @RequestParam Double amount) {
        User user = userRepo.findByUsername(username);
        if (user == null) return ResponseEntity.badRequest().body("User not found");

        user.setBalance(user.getBalance() + amount);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setSender("BANK");
        t.setReceiver(username);
        t.setAmount(amount);
        t.setType("deposit");
        t.setTimestamp(LocalDateTime.now());
        txRepo.save(t);

        return ResponseEntity.ok("Deposit successful");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(@RequestParam String username, @RequestParam Double amount) {
        User user = userRepo.findByUsername(username);
        if (user == null) return ResponseEntity.badRequest().body("User not found");
        if (user.getBalance() < amount) return ResponseEntity.badRequest().body("Insufficient balance");

        user.setBalance(user.getBalance() - amount);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setSender(username);
        t.setReceiver("ATM");
        t.setAmount(amount);
        t.setType("withdraw");
        t.setTimestamp(LocalDateTime.now());
        txRepo.save(t);

        return ResponseEntity.ok("Withdrawal successful");
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam String username,
                                     @RequestParam(required = false) String type) {
        List<Transaction> list = txRepo.findBySenderOrReceiver(username, username);
        if (type != null) {
            list = list.stream().filter(t -> t.getType().equalsIgnoreCase(type)).collect(Collectors.toList());
        }
        list.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())); // Newest first
        return ResponseEntity.ok(list);
    }

    @GetMapping("/admin/all-users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepo.findAll());
    }
}
