package com.rishabh.booklending;

import org.springframework.boot.SpringApplication;

public class TestBookLendingApplication {

	public static void main(String[] args) {
		SpringApplication.from(BookLendingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
