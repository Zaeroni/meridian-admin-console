package com.meridian.admin.repo;

import com.meridian.admin.domain.OrderRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderRecord, UUID> {

    /** Rows of [day (UTC), order count, total cents], oldest day first. Days with no orders are absent. */
    @Query(value = "select cast(placed_at at time zone 'UTC' as date) as day, count(*) as orders, "
            + "coalesce(sum(total_cents), 0) as cents "
            + "from orders where placed_at >= :since group by 1 order by 1", nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("since") Instant since);
}
