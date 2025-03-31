/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.quickcomment;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.Objects;

/**
 * @author Tony Field
 * @since 2025-03-31 00:26
 */
@Serdeable
public class QuickComment {
    String name;
    String quickComment;
    String role;
    int team;
    Instant timestamp;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQuickComment() {
        return quickComment;
    }

    public void setQuickComment(String quickComment) {
        this.quickComment = quickComment;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        QuickComment that = (QuickComment) o;
        return team == that.team && Objects.equals(name, that.name) && Objects.equals(quickComment, that.quickComment) && Objects.equals(role, that.role) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(quickComment);
        result = 31 * result + Objects.hashCode(role);
        result = 31 * result + team;
        result = 31 * result + Objects.hashCode(timestamp);
        return result;
    }
}
