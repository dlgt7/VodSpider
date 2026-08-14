package com.github.catvod.utils;

import com.github.catvod.crawler.SpiderDebug;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * 数学表达式求值器（双栈中缀求值）
 * 支持 + - * / % 运算符和括号 ()
 * 使用 BigDecimal 保证精度，除法保留 2 位小数
 */
public final class MathEval {

    private static final Map<String, Integer> OPERATOR_PRECEDENCE = new HashMap<>();

    static {
        OPERATOR_PRECEDENCE.put("(", 0);
        OPERATOR_PRECEDENCE.put("*", 1);
        OPERATOR_PRECEDENCE.put("/", 1);
        OPERATOR_PRECEDENCE.put("%", 1);
        OPERATOR_PRECEDENCE.put("+", 2);
        OPERATOR_PRECEDENCE.put("-", 2);
    }

    /**
     * 求值数学表达式
     */
    public static double evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) return 0;
        try {
            return evalExpression(expression.trim());
        } catch (Exception e) {
            SpiderDebug.log("MathEval error: " + e.getMessage());
            return 0;
        }
    }

    private static BigDecimal oneOperation(String op, BigDecimal left, BigDecimal right) {
        switch (op) {
            case "%":
                if (right.compareTo(BigDecimal.ZERO) != 0) {
                    return left.remainder(right);
                }
                return BigDecimal.ZERO;
            case "*":
                return left.multiply(right);
            case "+":
                return left.add(right);
            case "-":
                return left.subtract(right);
            case "/":
                if (right.compareTo(BigDecimal.ZERO) != 0) {
                    return left.divide(right, 2, RoundingMode.HALF_UP);
                }
                return BigDecimal.ZERO;
            default:
                return BigDecimal.ZERO;
        }
    }

    private static double evalExpression(String expression) {
        Stack<String> operatorStack = new Stack<>();
        Stack<BigDecimal> valueStack = new Stack<>();
        StringBuilder numBuf = new StringBuilder();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if ((c < '0' || c > '9') && c != '.') {
                if (numBuf.length() > 0) {
                    valueStack.push(new BigDecimal(numBuf.toString()));
                    numBuf.delete(0, numBuf.length());
                }
                String op = String.valueOf(c);
                if (operatorStack.isEmpty()) {
                    operatorStack.push(op);
                    continue;
                }
                if ("(".equals(op)) {
                    operatorStack.push(op);
                    continue;
                }
                if (")".equals(op)) {
                    while (!operatorStack.isEmpty() && !"(".equals(operatorStack.peek())) {
                        applyOperator(valueStack, operatorStack.pop());
                    }
                    if (!operatorStack.isEmpty()) {
                        operatorStack.pop(); // pop '('
                    }
                    continue;
                }
                while (!operatorStack.isEmpty()
                        && OPERATOR_PRECEDENCE.getOrDefault(operatorStack.peek(), 0)
                        <= OPERATOR_PRECEDENCE.getOrDefault(op, 0)) {
                    applyOperator(valueStack, operatorStack.pop());
                }
                operatorStack.push(op);
            } else {
                numBuf.append(c);
            }
        }
        if (numBuf.length() > 0) {
            valueStack.push(new BigDecimal(numBuf.toString()));
        }
        while (!operatorStack.isEmpty()) {
            applyOperator(valueStack, operatorStack.pop());
        }
        return valueStack.isEmpty() ? 0 : valueStack.pop().doubleValue();
    }

    private static void applyOperator(Stack<BigDecimal> valueStack, String op) {
        if (valueStack.size() < 2) return;
        BigDecimal right = valueStack.pop();
        BigDecimal left = valueStack.pop();
        valueStack.push(oneOperation(op, left, right));
    }
}
