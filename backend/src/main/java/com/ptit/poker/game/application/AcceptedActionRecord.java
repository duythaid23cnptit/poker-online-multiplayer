package com.ptit.poker.game.application;

/** Application acknowledgement for one durably recorded accepted action. */
public record AcceptedActionRecord(long id, long actionSequence) {}
