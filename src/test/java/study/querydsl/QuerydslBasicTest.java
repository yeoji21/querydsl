package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
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
    void startJPQL(){
        String qlString = "select m from Member m where m.username=:username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void startQuerydsl(){

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void search(){
        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void resultFetch(){
//        List<Member> fetch = queryFactory.selectFrom(member).fetch();
//
//        Member fetchOne = queryFactory.selectFrom(QMember.member).fetchOne();
//
//        Member fetchFirst = queryFactory.selectFrom(QMember.member).fetchFirst();

        QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults();
        long total = results.getTotal();
        List<Member> memberList = results.getResults();

        long fetchCount = queryFactory.selectFrom(member).fetchCount();
    }

    @Test
    void sort(){
        /*
        1. 회원 나이 내림차순 (desc)
        2. 회원 이름 오름차순 (asc)
        단, 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
         */
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory.selectFrom(member).where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        assertThat(result.get(0).getUsername()).isEqualTo("member5");
        assertThat(result.get(1).getUsername()).isEqualTo("member6");
        assertThat(result.get(2).getUsername()).isNull();

    }

    @Test
    void paging(){
        List<Member> result = queryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void aggregation(){
        List<Tuple> result = queryFactory.select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
        ).from(member).fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    @Test
    void group(){
        List<Tuple> result = queryFactory.select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    @Test
    void join(){
        List<Member> result = queryFactory.selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result).extracting("username")
                .containsExactly("member1", "member2");
    }

    /*
    회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
    JPQL : select m, t from Member m left join m.team t on t.name ='teamA'
     */
    @Test
    void join_on_filtering(){
        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        result.forEach(System.out::println);
    }


    /*
    연관관계 없는 엔티티 외부 조인
    회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    void join_on_no_relation(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        result.forEach(System.out::println);
    }

    @Test
    void fetch_join(){
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .join(member.team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        System.out.println(findMember.getTeam());
    }

    /*
    나이가 가장 많은 회원 조회
     */
    @Test
    void subQuery(){

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max())
                                .from(memberSub)
                )).fetch();

        assertThat(result.get(0)).extracting("age").isEqualTo(40);
    }



    /*
    나이가 평균 이상인 회원
     */
    @Test
    void subQueryGoe(){

        QMember subMember = new QMember("subMember");

        List<Member> result = queryFactory.selectFrom(member)
                .where(
                        member.age.goe(
                                JPAExpressions.select(subMember.age.avg())
                                        .from(subMember)
                        )
                ).fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void subQueryIn(){
        QMember subMember = new QMember("subMember");
        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.in(
                        JPAExpressions.select(subMember.age)
                                .from(subMember).where(subMember.age.gt(10))
                )).fetch();

        assertThat(result).extracting("age").containsExactly(20, 30, 40);
    }

    @Test
    void selectSubQuery(){
        QMember subMember = new QMember("subMember");

        List<Tuple> result = queryFactory.select(member.username,
                        JPAExpressions.select(subMember.age.avg()).from(subMember))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void basicCase(){
        List<String> result = queryFactory.select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        result.forEach(System.out::println);
    }

    @Test
    void complexCase(){
        List<String> result = queryFactory.select(
                new CaseBuilder()
                        .when(member.age.between(0, 20)).then("미성년자")
                        .when(member.age.between(21, 30)).then("20대")
                        .otherwise("기타")
        ).from(member).fetch();
        result.forEach(System.out::println);
    }

    @Test
    void constant(){
        List<Tuple> result = queryFactory.select(member.username, Expressions.constant("A"))
                .from(member).fetch();

        result.forEach(System.out::println);
    }

    @Test
    void concat(){
        List<String> result = queryFactory.select(member.username.concat("_")
                        .concat(member.age.stringValue()))
                .from(member).fetch();
        result.forEach(System.out::println);
    }
}
