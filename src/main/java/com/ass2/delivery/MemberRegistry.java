package com.ass2.delivery;

import com.ass2.common.BankReplica;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemberRegistry {
	private final Map<String, Member> membersByName = new LinkedHashMap<>();
	private final Map<String, LinkedHashMap<String, Member>> membersByGroup = new LinkedHashMap<>();

	public synchronized void add(String group, String name, BankReplica replica) throws RemoteException {
		if (membersByName.containsKey(name)) {
			throw new RemoteException("Replica name is already registered: " + name);
		}

		Member member = new Member(group, name, replica);
		membersByName.put(name, member);
		membersByGroup.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).put(name, member);
	}

	public synchronized Member find(String name) {
		return membersByName.get(name);
	}

	public synchronized List<Member> members(String group) {
		Map<String, Member> groupMembers = membersByGroup.get(group);
		return groupMembers == null ? List.of() : new ArrayList<>(groupMembers.values());
	}

	public synchronized List<String> names(String group) {
		Map<String, Member> groupMembers = membersByGroup.get(group);
		return groupMembers == null ? List.of() : List.copyOf(groupMembers.keySet());
	}

	public synchronized Member remove(String name) {
		Member member = membersByName.remove(name);
		if (member != null) {
			removeFromGroup(member);
		}
		return member;
	}

	public synchronized boolean remove(Member member) {
		if (membersByName.get(member.name()) != member) {
			return false;
		}
		membersByName.remove(member.name());
		removeFromGroup(member);
		return true;
	}

	private void removeFromGroup(Member member) {
		LinkedHashMap<String, Member> groupMembers = membersByGroup.get(member.group());
		if (groupMembers != null) {
			groupMembers.remove(member.name());
			if (groupMembers.isEmpty()) {
				membersByGroup.remove(member.group());
			}
		}
	}

	public record Member(String group, String name, BankReplica replica) {
	}
}
