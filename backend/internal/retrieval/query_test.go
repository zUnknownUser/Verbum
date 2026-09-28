package retrieval

import (
	"reflect"
	"strings"
	"testing"
)

func TestConversationalNormalization(t *testing.T) {
	want := []string{"god | lord | yahweh", "speak | say | said | answer | talk", "job"}
	for _, q := range []string{"Önde Deus conversa com Jó?", "onde deus conversa com JÓ", "Where does God speak to Job?"} {
		if got := Terms(q); !reflect.DeepEqual(got, want) {
			t.Errorf("%q: %v", q, got)
		}
	}
	for q, want := range map[string]string{"João": "john", "joaninha": "joaninha"} {
		if got := Terms(q); !reflect.DeepEqual(got, []string{want}) {
			t.Fatal(got)
		}
	}
}
func TestTermsBoundDeduplicationAndSyntax(t *testing.T) {
	if got := Terms("deus GOD senhor Yahweh"); len(got) != 1 {
		t.Fatal(got)
	}
	if got := Terms("  "); len(got) != 0 {
		t.Fatal(got)
	}
	if got := Terms("!deus:* & 'job' | (JOÃO)"); len(got) != 3 {
		t.Fatal(got)
	}
	terms := Terms(strings.Repeat("abc ", 100) + " one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen")
	if len(terms) != 16 {
		t.Fatal(len(terms))
	}
}
