package com.s24.search.solr.query.bmax;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AbstractLuceneQueryTest {

   public ClauseMatcher tq(Occur occur, float boost, String field, String text) {
      return c(occur, tq(boost, field, text));
   }

   public ClauseMatcher tq(Occur occur, String field, String text) {
      return c(occur, tq(field, text));
   }

   public static TQMatcher tq(float boost, String field, String text) {
      return new TQMatcher(boost, field, text);
   }

   public static TQMatcher tq(String field, String text) {
      return tq(1f, field, text);
   }
   
   public static BQMatcher bq(float boost, int mm, ClauseMatcher... clauses) {
      return new BQMatcher(boost, mm, clauses);
   }

   public static BQMatcher bq(float boost, ClauseMatcher... clauses) {
      return new BQMatcher(boost, 0, clauses);
   }

   public static BQMatcher bq(ClauseMatcher... clauses) {
      return bq(1f, clauses);
   }

   public static PhraseQueryMatcher phrase(float boost, String field, int slop, String... terms) {
       return new PhraseQueryMatcher(boost, field, terms, slop);
   }
   
   public ClauseMatcher all(Occur occur) {
       return c(occur, all());
   }
   
   public AllDocsQueryMatcher all() {return  new AllDocsQueryMatcher(); }

   public ClauseMatcher bq(Occur occur, float boost, int mm, ClauseMatcher... clauses) {
      return c(occur, bq(boost, mm, clauses));
   }

   public ClauseMatcher bq(Occur occur, float boost, ClauseMatcher... clauses) {
      return c(occur, bq(boost, clauses));
   }

   public ClauseMatcher bq(Occur occur, ClauseMatcher... clauses) {
      return c(occur, bq(clauses));
   }

   @SafeVarargs
   public static DMQMatcher dmq(float boost, float tieBreaker, TypeSafeMatcher<? extends Query>... disjuncts) {
      return new DMQMatcher(boost, tieBreaker, disjuncts);
   }

   @SafeVarargs
   public static final DMQMatcher dmq(float boost, TypeSafeMatcher<? extends Query>... disjuncts) {
      return dmq(boost, 0.0f, disjuncts);
   }

   @SafeVarargs
   public static final DMQMatcher dmq(TypeSafeMatcher<? extends Query>... disjuncts) {
      return dmq(1f, 0.0f, disjuncts);
   }

   @SafeVarargs
   public final ClauseMatcher dmq(Occur occur, float boost, float tieBreaker,
                                  TypeSafeMatcher<? extends Query>... disjuncts) {
      return c(occur, dmq(boost, tieBreaker, disjuncts));
   }

   @SafeVarargs
   public final ClauseMatcher dmq(Occur occur, float boost, TypeSafeMatcher<? extends Query>... disjuncts) {
      return c(occur, dmq(boost, disjuncts));
   }

   @SafeVarargs
   public final ClauseMatcher dmq(Occur occur, TypeSafeMatcher<? extends Query>... disjuncts) {
      return c(occur, dmq(disjuncts));
   }

   public static ClauseMatcher c(Occur occur, TypeSafeMatcher<? extends Query> queryMatcher) {
      return new ClauseMatcher(occur, queryMatcher);
   }

   static class ClauseMatcher extends TypeSafeMatcher<BooleanClause> {

      Occur occur;
      TypeSafeMatcher<? extends Query> queryMatcher;

      public ClauseMatcher(Occur occur, TypeSafeMatcher<? extends Query> queryMatcher) {
         this.occur = occur;
         this.queryMatcher = queryMatcher;
      }

      @Override
      public void describeTo(Description description) {
         description.appendText("occur: " + occur.name() + ", query: ");
         queryMatcher.describeTo(description);
      }

      @Override
      protected boolean matchesSafely(BooleanClause clause) {
         return clause.getOccur() == occur && queryMatcher.matches(clause.getQuery());
      }

   }

    static class DMQMatcher extends TypeSafeMatcher<Query> {
        float boost;
        float tieBreaker;
        TypeSafeMatcher<? extends Query>[] disjuncts;

        @SafeVarargs
        public DMQMatcher(float boost, float tieBreaker, TypeSafeMatcher<? extends Query>... disjuncts) {
            super((boost == 1f) ? DisjunctionMaxQuery.class : BoostQuery.class);
            this.boost = boost;
            this.tieBreaker = tieBreaker;
            this.disjuncts = disjuncts;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("DMQ: tie=" + tieBreaker + ", boost=" + boost + ", ");
            description.appendList("disjuncts:[", ",\n", "]", Arrays.asList(disjuncts));
        }

        @Override
        protected boolean matchesSafely(Query query) {

            DisjunctionMaxQuery dmq;

            if (query instanceof BoostQuery) {
                BoostQuery boostQuery = (BoostQuery) query;
                if (boostQuery.getBoost() != boost) {
                    return false;
                } else {
                    dmq = (DisjunctionMaxQuery) boostQuery.getQuery();
                }
            } else {
                if (boost != 1f) {
                    return false;
                } else {
                    dmq = (DisjunctionMaxQuery) query;
                }
            }

            return matchDisjunctionMaxQuery(dmq);

        }

        protected boolean matchDisjunctionMaxQuery(DisjunctionMaxQuery dmq) {

            if (tieBreaker != dmq.getTieBreakerMultiplier()) {
                return false;
            }

            List<Query> dmqDisjuncts = dmq.getDisjuncts();
            if (dmqDisjuncts == null || dmqDisjuncts.size() != disjuncts.length) {
                return false;
            }

            for (TypeSafeMatcher<? extends Query> disjunct : disjuncts) {
                boolean found = false;
                for (Query q : dmqDisjuncts) {
                    found = disjunct.matches(q);
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    return false;
                }

            }
            return true;
        }
    }
   
    class AllDocsQueryMatcher extends TypeSafeMatcher<Query> {

        @Override
        public void describeTo(Description description) {
            description.appendText("AllDocs");
        }

        @Override
        protected boolean matchesSafely(Query item) {
            return MatchAllDocsQuery.class.isAssignableFrom(item.getClass());
        }
       
    }

    static class BQMatcher extends TypeSafeMatcher<Query> {

        ClauseMatcher[] clauses;
        int mm;
        float boost;

        public BQMatcher(float boost, int mm, ClauseMatcher... clauses) {
            super((boost == 1f) ? BooleanQuery.class : BoostQuery.class);
            this.clauses = clauses;
            this.boost = boost;
            this.mm = mm;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("BQ: mm=" + mm + ", boost=" + boost + ", ");
            description.appendList("clauses:[", ",\n", "]", Arrays.asList(clauses));
        }

        @Override
        protected boolean matchesSafely(Query query) {

            BooleanQuery bq = null;

            if (query instanceof BoostQuery) {

                Query boostedQuery = ((BoostQuery) query).getQuery();
                if (!(boostedQuery instanceof BooleanQuery)) {
                    return false;
                }

                if (((BoostQuery) query).getBoost() != boost) {
                    return false;
                }

                bq = (BooleanQuery) boostedQuery;

            } else if (!(query instanceof BooleanQuery)) {
                return false;
            } else {
                if (boost != 1f) {
                    return false;
                }
                bq = (BooleanQuery) query;
            }

            return matchBooleanQuery(bq);

        }

        protected boolean matchBooleanQuery(BooleanQuery bq) {

            if (mm != bq.getMinimumNumberShouldMatch()) {
                return false;
            }

            List<BooleanClause> bqClauses = bq.clauses();
            if (bqClauses == null || bqClauses.size() != clauses.length) {
                return false;
            }

            for (int i = 0; i < clauses.length; i++) {

                boolean found = false;
                for (BooleanClause clause : bqClauses) {
                    found = clauses[i].matches(clause);
                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    return false;
                }

            }
            return true;
        }

    }

    static class PhraseQueryMatcher extends TypeSafeMatcher<Query> {

       final String field;
       final int slop;
       final String[] terms;
       final float boost;

       public PhraseQueryMatcher(float boost, final String field, final String[] terms, final int slop) {
           super((boost == 1f) ? PhraseQuery.class : BoostQuery.class);

           this.field = field;
           this.slop = slop;
           this.terms = terms;
           this.boost = boost;

       }

       @Override
       protected boolean matchesSafely(Query query) {

           PhraseQuery pq = null;

           if (query instanceof BoostQuery) {

               Query boostedQuery = ((BoostQuery) query).getQuery();
               if (!(boostedQuery instanceof PhraseQuery)) {
                   return false;
               }

               if (((BoostQuery) query).getBoost() != boost) {
                   return false;
               }

               pq = (PhraseQuery) boostedQuery;

           } else if (!(query instanceof PhraseQuery)) {
               return false;
           } else {
               if (boost != 1f) {
                   return false;
               }
               pq = (PhraseQuery) query;
           }

           return matchesPhraseQuery(pq);

       }

        protected boolean matchesPhraseQuery(PhraseQuery item) {
            final Term[] phraseTerms = item.getTerms();
            if (phraseTerms.length != terms.length || slop != item.getSlop()) {
                return false;
            }
            for (int i = 0; i < terms.length; i++) {
                if (!phraseTerms[i].equals(new Term(field, terms[i]))) {
                    return false;
                }
            }

            return true;
        }



       @Override
       public void describeTo(Description description) {
           description.appendText("PQ: " + field + ":\"" + Arrays.stream(terms).collect(Collectors.joining(" "))  + "\"~" + slop + " ^" + boost);
       }
    }

    static class TQMatcher extends TypeSafeMatcher<Query> {

      final String field;
      final String text;
      final float boost;

      public TQMatcher(float boost, String field, String text) {
         super(TermQuery.class);
         this.field = field;
         this.text = text;
         this.boost = boost;
      }

      @Override
      public void describeTo(Description description) {
         description.appendText("TQ field: " + field + ", text: " + text + ", boost: " + boost);

      }

      @Override
      protected boolean matchesSafely(Query query) {

          if (query instanceof BoostQuery) {

              BoostQuery boostQuery = (BoostQuery) query;

              Query boostedQuery = boostQuery.getQuery();
              if (boostedQuery instanceof TermQuery) {
                  Term term = ((TermQuery) query).getTerm();
                  return field.equals(term.field())
                          && text.equals(term.text())
                          && boostQuery.getBoost() == boost;
              } else {
                  return false;
              }
          } else if (query instanceof TermQuery) {
              Term term = ((TermQuery) query).getTerm();
              return field.equals(term.field())
                      && text.equals(term.text())
                      && boost == 1f;
          } else {
              return false;
          }

      }

   }

}
