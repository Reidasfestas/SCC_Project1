package tukano.impl.data;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import utils.CosmosContainerName;

@Entity
@CosmosContainerName("likes")
public class Likes {

	@Id
	@JsonProperty("id")
	@Column(name = "id")
	String id;
	
	@Id
	@Column(name = "userId")
	String userId;
	
	@Id
	@Column(name = "shortId")
	String shortId;
	
	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	@Column(name = "ownerId")
	String ownerId;
	
	public Likes() {}

	public Likes(String userId, String shortId, String ownerId) {
		this.userId = userId;
		this.shortId = shortId;
		this.ownerId = ownerId;
		this.id = userId + "-" + shortId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	@Override
	public String toString() {
		return "Likes [userId=" + userId + ", shortId=" + shortId + ", ownerId=" + ownerId + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(ownerId, shortId, userId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Likes other = (Likes) obj;
		return Objects.equals(ownerId, other.ownerId) && Objects.equals(shortId, other.shortId)
				&& Objects.equals(userId, other.userId);
	}
	
	
}
