package ask

import "testing"

func TestNeedsProfessionalHelpNote(t *testing.T) {
	cases := map[string]bool{
		"I feel hopeless and depressed, what does the bible say": true,
		"I want to end my life":                                  true,
		"my spouse is abusive, what should I do":                 true,
		"I'm filing for bankruptcy, any Bible verses on money":   true,
		"why did job suffer":                                     false,
		"what is the meaning of Genesis 1:1":                     false,
	}
	for question, want := range cases {
		if got := needsProfessionalHelpNote(question); got != want {
			t.Errorf("needsProfessionalHelpNote(%q) = %v, want %v", question, got, want)
		}
	}
}

func TestMentionsProfessionalHelp(t *testing.T) {
	if !mentionsProfessionalHelp("please see a therapist") {
		t.Error("want true for an answer that already mentions a therapist")
	}
	if mentionsProfessionalHelp("God cares for you deeply") {
		t.Error("want false for an answer with no professional-help language")
	}
}
