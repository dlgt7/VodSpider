package com.github.catvod.utils;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Shell {

    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "chmod",
            "ls",
            "cat",
            "echo",
            "mkdir",
            "rm",
            "mv",
            "cp"
    ));

    private static final Set<String> DANGEROUS_PATTERNS = new HashSet<>(Arrays.asList(
            "rm -rf /",
            "rm -rf /*",
            "dd if=",
            "mkfs",
            "format",
            "del /",
            "format c:",
            "> /dev/",
            "shutdown",
            "reboot",
            "init 0",
            "init 6",
            "halt",
            "poweroff"
    ));

    public static void exec(String command) {
        if (TextUtils.isEmpty(command)) {
            SpiderDebug.log("Shell: empty command");
            return;
        }
        
        if (!isCommandSafe(command)) {
            SpiderDebug.log("Shell: blocked dangerous command: " + command);
            throw new SecurityException("Command not allowed for security reasons");
        }
        
        try {
            String[] parts = command.split("\\s+");
            String cmd = parts[0];
            
            if (!ALLOWED_COMMANDS.contains(cmd)) {
                SpiderDebug.log("Shell: command not in whitelist: " + cmd);
                throw new SecurityException("Command not in whitelist: " + cmd);
            }
            
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
                reader.close();
                SpiderDebug.log("Shell command failed with exit code " + exitCode + ": " + error.toString());
                throw new RuntimeException("Shell command failed: " + error.toString());
            }
            
            SpiderDebug.log("Shell: command executed successfully: " + cmd);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            SpiderDebug.log(e);
            throw new RuntimeException("Shell execution failed: " + e.getMessage());
        }
    }

    public static String execute(String command) {
        if (TextUtils.isEmpty(command)) {
            return "";
        }
        
        if (!isCommandSafe(command)) {
            SpiderDebug.log("Shell: blocked dangerous command: " + command);
            throw new SecurityException("Command not allowed for security reasons");
        }
        
        try {
            String[] parts = command.split("\\s+");
            String cmd = parts[0];
            
            if (!ALLOWED_COMMANDS.contains(cmd)) {
                SpiderDebug.log("Shell: command not in whitelist: " + cmd);
                throw new SecurityException("Command not in whitelist: " + cmd);
            }
            
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            reader.close();
            
            if (exitCode != 0) {
                SpiderDebug.log("Shell command failed with exit code " + exitCode);
                return "";
            }
            
            return output.toString().trim();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private static boolean isCommandSafe(String command) {
        if (TextUtils.isEmpty(command)) return false;
        
        String lowerCommand = command.toLowerCase().trim();
        
        for (String dangerous : DANGEROUS_PATTERNS) {
            if (lowerCommand.contains(dangerous.toLowerCase())) {
                return false;
            }
        }
        
        if (lowerCommand.contains(";") || lowerCommand.contains("&&") || lowerCommand.contains("||")) {
            return false;
        }
        
        if (lowerCommand.contains("|") && !lowerCommand.equals("ls |")) {
            return false;
        }
        
        if (lowerCommand.contains(">") || lowerCommand.contains(">>")) {
            String[] parts = lowerCommand.split(">");
            if (parts.length > 1) {
                String target = parts[parts.length - 1].trim();
                if (target.startsWith("/system") || target.startsWith("/dev") || target.startsWith("/proc")) {
                    return false;
                }
            }
        }
        
        return true;
    }

    public static boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getSystemProperty(String key) {
        if (TextUtils.isEmpty(key)) return "";
        try {
            Process process = Runtime.getRuntime().exec("getprop " + key);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            reader.close();
            process.waitFor();
            return value != null ? value.trim() : "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}