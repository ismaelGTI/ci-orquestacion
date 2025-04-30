package com.example;

    public class Calculator {
        public int add(int a, int b) {
            return a + b;
        }

        public static void main(String[] args) {
            Calculator calculator = new Calculator();
            int result = calculator.add(2, 3);
            System.out.println("Result: " + result);
        }
    }