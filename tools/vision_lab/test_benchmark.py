import unittest,copy
from benchmark import score
R={"documents":{"a":{"pages":[{"index":0,"printed_numbers":[{"n":1,"x":100,"y":100},{"n":2,"x":100,"y":600}],"last_ink_points":[{"n":1,"x":300,"y":450},{"n":2,"x":300,"y":850}]}]}}}
P={"documents":{"a":{"pages":[{"index":0,"questions":[{"number":1,"left":90,"top":90,"right":460,"bottom":490},{"number":2,"left":90,"top":590,"right":460,"bottom":970}]}]}}}
class Tests(unittest.TestCase):
 def test_good(self):self.assertTrue(score(R,P)["accepted"])
 def test_cut_diagram(self):
  q=copy.deepcopy(P);q["documents"]["a"]["pages"][0]["questions"][0]["bottom"]=300
  self.assertFalse(score(R,q)["accepted"])
 def test_missing_question(self):
  q=copy.deepcopy(P);q["documents"]["a"]["pages"][0]["questions"].pop()
  self.assertFalse(score(R,q)["accepted"])
 def test_merged_questions(self):
  q=copy.deepcopy(P);q["documents"]["a"]["pages"][0]["questions"][0]["bottom"]=800
  self.assertFalse(score(R,q)["accepted"])
 def test_missing_page(self):self.assertEqual(1,score(R,{"documents":{}})["missing_pages"])
if __name__=="__main__":unittest.main()
