package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
public class QuerydslMiddleTest {
    @Autowired
    EntityManager em;
    private JPAQueryFactory queryFactory;

    @BeforeEach
    public void setUp() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void tupleProjection(){
        List<Tuple> result = queryFactory.select(member.username, member.age)
                .from(member).fetch();

        result.forEach(t -> {
            System.out.println("username : " + t.get(member.username));
            System.out.println("age : " + t.get(member.age));
        });

        // Tuple 객체는 querydsl 패키지 내에 있는 객체이기 때문에 respository단에서 쓰는건
        // 괜찮지만, service나 controller단으로 가져가는건 좋은 설계가 아님
        // querydsl의 사용을 노출할뿐더러 상위 계층에서 querydsl에 대한 의존성이 강해지기 때문
    }

    @Test
    void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m"
                , MemberDto.class).getResultList();

        result.forEach(System.out::println);
    }

    @Test
    void JPQLResultMapping(){
        List<Member> result = em.createQuery("select m from Member m", Member.class).getResultList();
        List<MemberDto> dtoList = result.stream().map(m -> new MemberDto(m.getUsername(), m.getAge())).collect(Collectors.toList());
        dtoList.forEach(System.out::println);
    }


    /*
        프로젝션을 DTO로 받는 세 가지 방법
     */
    @Test
    void findDtoBySetter(){
        //setter가 있어야 함
        List<MemberDto> result = queryFactory.select(Projections.bean(MemberDto.class,
                        member.username, member.age))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void findDtoByField(){
        List<MemberDto> result = queryFactory.select(Projections.fields(MemberDto.class,
                        member.username, member.age))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void findDtoByConstructor(){
        List<MemberDto> result = queryFactory.select(Projections.constructor(MemberDto.class,
                        member.username, member.age))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void findUserDtoByField(){
        // dto와 이름이 안맞을 경우에는, as로 매칭시켜줘야 함
        List<UserDto> result = queryFactory.select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        member.age))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void subQueryDtoMatching(){
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory.select(Projections.fields(UserDto.class,
                member.username.as("name"),
                ExpressionUtils.as(JPAExpressions
                        .select(memberSub.age.max())
                        .from(memberSub), "age")
        )).from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory.select(new QMemberDto(member.username, member.age))
                .from(member).fetch();

        result.forEach(System.out::println);

        // 이때 querydsl-core랑 다른거 버전 맞춰주세요ㅋ
    }

    // 동적쿼리
    @Test
    void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        BooleanBuilder builder = new BooleanBuilder();

        if (usernameParam != null)
            builder.and(member.username.eq(usernameParam));
        if (ageParam != null)
            builder.and(member.age.eq(ageParam));

        return queryFactory.selectFrom(member).where(builder).fetch();
    }

    @Test
    void dynamicQuery_where(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory.selectFrom(member)
//                .where(usernameEq(usernameParam), ageEq(ageParam))
                .where(allEq(usernameParam, ageParam))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameParam) {
        return usernameParam!=null ? member.username.eq(usernameParam) : null;
    }

    private BooleanExpression ageEq(Integer ageParam) {
        return ageParam!=null ? member.age.eq(ageParam) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    @Test
    void bulkUpdate(){
        long count = queryFactory.update(member).set(member.username, "비회원")
                .where(member.age.lt(24)).execute();

        // 벌크 연산 후엔 영속성 컨텍스트 초기화할 것
        em.flush();
        em.clear();

        List<Member> result = queryFactory.selectFrom(member).fetch();
        result.forEach(System.out::println);
    }

    @Test
    void bulkAdd(){
        long count = queryFactory.update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    @Test
    void bulkDelete(){
        long count = queryFactory.delete(member).where(member.age.gt(18)).execute();
    }

    @Test
    void sqlFunction(){
        List<String> result = queryFactory.select(Expressions.stringTemplate(
                "function('replace', {0}, {1}, {2})", member.username, "member", "M"
        )).from(member).fetch();

        result.forEach(System.out::println);
    }
}
