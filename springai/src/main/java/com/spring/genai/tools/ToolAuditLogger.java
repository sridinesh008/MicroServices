package com.spring.genai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One log line per assistant tool invocation -- who, which tool, what arguments, what
 * happened. Separate from {@code SecurityAuditLogger} (auth events) since this is about what
 * the LLM did on a user's behalf, not who logged in.
 */
@Component
public class ToolAuditLogger {

	private static final Logger log = LoggerFactory.getLogger("TOOL_AUDIT");

	public void logInvocation(String username, String toolName, String argsSummary) {
		log.info("tool invoked user={} tool={} args={}", username, toolName, argsSummary);
	}

	public void logOutcome(String username, String toolName, String outcomeSummary) {
		log.info("tool completed user={} tool={} outcome={}", username, toolName, outcomeSummary);
	}

	public void logDenied(String username, String toolName, String reason) {
		log.warn("tool denied user={} tool={} reason={}", username, toolName, reason);
	}

}
