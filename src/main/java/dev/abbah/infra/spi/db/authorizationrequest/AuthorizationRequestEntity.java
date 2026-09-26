package dev.abbah.infra.spi.db.authorizationrequest;

import dev.abbah.domain.authorizationrequest.AuthorizationRequest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A mutable class rather than a record: Jakarta Persistence doesn't support records as entities.
 */
@Entity
@Table(name = "authorization_request")
public class AuthorizationRequestEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    public AuthorizationRequest.Type type;

    @Column(name = "requested_by")
    public String requestedBy;

    @Column(name = "requested_at")
    public Instant requestedAt;

    public String subject;

    public String description;

    @Column(name = "employee_id")
    public String employeeId;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    public String email;

    public String department;

    @Column(name = "start_date")
    public LocalDate startDate;

    @ElementCollection
    @CollectionTable(name = "authorization_request_item", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "authorization_code")
    public List<String> authorizations;
}
