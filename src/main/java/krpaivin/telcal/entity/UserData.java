package krpaivin.telcal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "user_data")
public class UserData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private String userId;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "expiration_time_token")
    private String expirationTimeToken;

    @Column(name = "calendar")
    private String calendar;

    @Column(name = "keywords")
    private String keywords;

    @Column(name = "default_keyword")
    private String defaultKeyword;

    @Column(name = "compound_keywords")
    private String compoundKeywords;

    @Version
    private Long version;
}
