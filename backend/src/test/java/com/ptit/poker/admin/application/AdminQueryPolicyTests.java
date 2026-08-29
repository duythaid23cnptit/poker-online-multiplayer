package com.ptit.poker.admin.application;

import static org.assertj.core.api.Assertions.*;import java.time.Instant;import java.util.Set;import org.junit.jupiter.api.Test;import org.springframework.web.server.ResponseStatusException;

class AdminQueryPolicyTests {
 @Test void acceptsPaginationBoundaries(){assertThatCode(()->AdminQueryPolicy.pagination(0,1)).doesNotThrowAnyException();assertThatCode(()->AdminQueryPolicy.pagination(2,100)).doesNotThrowAnyException();}
 @Test void rejectsInvalidPagination(){assertThatThrownBy(()->AdminQueryPolicy.pagination(-1,20)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->AdminQueryPolicy.pagination(0,101)).isInstanceOf(ResponseStatusException.class);}
 @Test void normalizesOptionalFilters(){assertThat(AdminQueryPolicy.optional("  alice ")).isEqualTo("alice");assertThat(AdminQueryPolicy.optional("  ")).isNull();}
 @Test void validatesWhitelistedFilters(){assertThat(AdminQueryPolicy.allowed(" admin ",Set.of("ADMIN","PLAYER"))).isEqualTo("ADMIN");assertThatThrownBy(()->AdminQueryPolicy.allowed("root",Set.of("ADMIN"))).isInstanceOf(ResponseStatusException.class);}
 @Test void validatesBoundedDateRanges(){Instant start=Instant.parse("2026-01-01T00:00:00Z");assertThatCode(()->AdminQueryPolicy.dates(start,start.plusSeconds(86_400))).doesNotThrowAnyException();assertThatThrownBy(()->AdminQueryPolicy.dates(start.plusSeconds(1),start)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->AdminQueryPolicy.dates(start,start.plusSeconds(368L*86_400))).isInstanceOf(ResponseStatusException.class);}
 @Test void publicDtosExposeNoCredentialComponents(){for(Class<?> type:AdminModels.class.getDeclaredClasses())assertThat(type.getRecordComponents()).allSatisfy(component->assertThat(component.getName().toLowerCase()).doesNotContain("password","token","secret","holecards"));}
}
